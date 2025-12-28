package io.github.tomorrow615.compiler.backend.codegen;

import io.github.tomorrow615.compiler.backend.mips.MipsRegister;
import io.github.tomorrow615.compiler.backend.mips.assembly.*;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsBasicBlock;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import io.github.tomorrow615.compiler.backend.mips.operand.Operand;
import io.github.tomorrow615.compiler.backend.mips.operand.VirtualRegister;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 指令翻译器
 * 负责将 LLVM Instruction 翻译为 MIPS Instruction
 * 核心策略：虚拟寄存器分配 + Phi降级(Parallel Copy)
 */
public class InstTranslator {
    private final StackManager stackManager;
    private final MipsFunction mipsFunction;

    // 当前正在写入的 MIPS 基本块
    private MipsBasicBlock currentMipsBlock;
    // 当前正在处理的 LLVM 基本块 (用于 Phi 节点的前驱判断)
    private BasicBlock currentLLVMBlock;
    
    // [Phase 2] ValueMap: LLVM Value -> MIPS Operand (VirtualRegister)
    // 用于跟踪临时计算结果，避免不必要的栈访问
    private final Map<Value, Operand> valueMap;

    // [Phase 2 Fix] BlockMap: LLVM BasicBlock -> MipsBasicBlock
    // 用于构建 CFG (Liveness 分析依赖 CFG)
    private final Map<BasicBlock, MipsBasicBlock> blockMap;

    // [Optimization] Local Cache for Argument Loads
    // Reset per MipsBasicBlock to ensure dominance safety
    private final Map<Value, VirtualRegister> localArgCache;
    private MipsBasicBlock lastVisitedBlock;

    public InstTranslator(StackManager stackManager, MipsFunction mipsFunction) {
        this.stackManager = stackManager;
        this.mipsFunction = mipsFunction;
        this.valueMap = new HashMap<>();
        this.blockMap = new HashMap<>();
        this.localArgCache = new HashMap<>();
        this.lastVisitedBlock = null;
    }

    /**
     * 翻译整个函数的入口
     */
    public void translate(Function llvmFunction) {
        // 1. 第一遍扫描：创建所有 MipsBasicBlock 并建立映射
        // 这是为了在处理分支指令时能够获取到目标块的引用，从而构建 CFG
        for (BasicBlock llvmBB : llvmFunction.getBasicBlocks()) {
            String label = makeLabel(llvmBB);
            MipsBasicBlock mipsBB = new MipsBasicBlock(label);
            mipsFunction.addBasicBlock(mipsBB);
            blockMap.put(llvmBB, mipsBB);
        }

        // 2. 第二遍扫描：翻译块内的每条指令
        for (BasicBlock llvmBB : llvmFunction.getBasicBlocks()) {
            this.currentLLVMBlock = llvmBB;
            this.currentMipsBlock = blockMap.get(llvmBB);

            // [Phase 2 Fix] 预先为当前块的所有 Phi 指令创建 VReg
            // 确保即使前驱块（如循环后向边）尚未处理，Phi 的 VReg 也已存在，供块内后续指令引用
            for (Instruction inst : llvmBB.getInstructions()) {
                if (inst instanceof PhiInst phi) {
                    valueMap.computeIfAbsent(phi, k -> new VirtualRegister());
                } else {
                    break; // Phi 指令必定位于块开头
                }
            }

            for (Instruction inst : llvmBB.getInstructions()) {
                translateInstruction(inst);
            }
        }
    }

    private void translateInstruction(Instruction inst) {
        if (inst instanceof ReturnInst ret) {
            translateReturn(ret);
        } else if (inst instanceof BinaryOpInst bin) {
            translateBinary(bin);
        } else if (inst instanceof LoadInst load) {
            translateLoad(load);
        } else if (inst instanceof StoreInst store) {
            translateStore(store);
        } else if (inst instanceof AllocaInst) {
            // Alloca 不生成指令，空间已在栈上分配
        } else if (inst instanceof IcmpInst icmp) {
            translateIcmp(icmp);
        } else if (inst instanceof BranchInst br) {
            translateBranch(br);
        } else if (inst instanceof PhiInst) {
            // Phi 指令本身不生成代码
            // 它的逻辑在跳转指令发生前，由前驱块负责 Copy
        } else if (inst instanceof CallInst call) { // [新增]
            translateCall(call);
        } else if (inst instanceof GetElementPtrInst gep) { // [新增]
            translateGep(gep);
        } else if (inst instanceof ZextInst zext) { // [新增]
            translateZext(zext);
        } else if (inst instanceof TruncInst trunc) { // [新增] 建议一并加上
            translateZext(trunc); // 逻辑一样，复用即可
        }
        // TODO: Call, Gep (后续阶段实现)
    }

    // === 具体指令翻译 ===
    private void translateReturn(ReturnInst inst) {
        // 1. 如果有返回值，Move 到 $v0
        if (!inst.isVoidRet()) {
            // [Phase 2 修正] 使用 getOperand 获取返回值
            Operand retVal = getOperand(inst.getReturnValue());
            currentMipsBlock.addInstruction(new MipsMove(MipsRegister.V0, retVal));
        }

        // 2. 生成函数尾声 (Epilogue)
        // 恢复 $ra
        int raOffset = stackManager.getRaOffset();
        currentMipsBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.LW, MipsRegister.RA, MipsRegister.SP, raOffset));

        // 关栈
        int frameSize = stackManager.getFrameSize();
        if (frameSize > 0) {
            currentMipsBlock.addInstruction(new MipsBinary("addu", MipsRegister.SP, MipsRegister.SP, frameSize));
        }

        // [核心修复] 特判 main 函数的返回
        if (mipsFunction.getName().equals("main")) {
            // 如果是 main 函数，执行 syscall 10 终止程序
            // 否则 jr $ra 会跳到 0 地址导致崩溃
            currentMipsBlock.addInstruction(new MipsLi(MipsRegister.V0, 10)); // 10: exit
            currentMipsBlock.addInstruction(new MipsSyscall());
        } else {
            // 普通函数：跳转回调用者
            currentMipsBlock.addInstruction(new MipsBranch("jr", MipsRegister.RA));
        }
    }

    private void translateBinary(BinaryOpInst inst) {
        // [Phase 2 重构] 使用 getOperand 获取操作数（虚拟寄存器）
        Operand lhs = getOperand(inst.getLhs());
        Operand rhs = getOperand(inst.getRhs());
        VirtualRegister dest = new VirtualRegister();

        if (inst.getOp() == BinaryOpInst.OpCode.SDIV) {
            currentMipsBlock.addInstruction(new MipsBinary("div", lhs, rhs));
            currentMipsBlock.addInstruction(new MipsBinary("mflo", dest));
        } else if (inst.getOp() == BinaryOpInst.OpCode.SREM) {
            currentMipsBlock.addInstruction(new MipsBinary("div", lhs, rhs));
            currentMipsBlock.addInstruction(new MipsBinary("mfhi", dest));
        } else if (inst.getOp() == BinaryOpInst.OpCode.SHL) {
            currentMipsBlock.addInstruction(new MipsBinary("sllv", dest, lhs, rhs));
        } else if (inst.getOp() == BinaryOpInst.OpCode.ASHR) {
            currentMipsBlock.addInstruction(new MipsBinary("srav", dest, lhs, rhs));
        } else {
            String op = switch (inst.getOp()) {
                case ADD -> "addu";
                case SUB -> "subu";
                case MUL -> "mul";
                case AND -> "and";
                case OR -> "or";
                default -> "addu";
            };
            currentMipsBlock.addInstruction(new MipsBinary(op, dest, lhs, rhs));
        }
        // [Phase 2 关键]将结果存入 valueMap，而非立即写回栈
        valueMap.put(inst, dest);
    }

    private void translateLoad(LoadInst inst) {
        // [Phase 2 重构] Load 从内存读取，结果存入 VReg
        Operand ptr = getOperand(inst.getPointer());
        VirtualRegister dest = new VirtualRegister();
        currentMipsBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.LW, dest, ptr, 0));
        valueMap.put(inst, dest);
    }

       private void translateStore(StoreInst inst) {
        // [Phase 2 重构] Store 是真正的内存写入
        Operand value = getOperand(inst.getValue());
        Operand ptr = getOperand(inst.getPointer());
        currentMipsBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.SW, value, ptr, 0));
    }

    private void translateIcmp(IcmpInst inst) {
        // [Phase 2 重构] 使用虚拟寄存器
        Operand lhs = getOperand(inst.getLhs());
        Operand rhs = getOperand(inst.getRhs());
        VirtualRegister dest = new VirtualRegister();

        String op = switch (inst.getCmpType()) {
            case EQ -> "seq";
            case NE -> "sne";
            case SLT -> "slt";
            case SLE -> "sle";
            case SGT -> "sgt";
            case SGE -> "sge";
        };

        currentMipsBlock.addInstruction(new MipsCompare(op, dest, lhs, rhs));
        valueMap.put(inst, dest);
    }

    private void translateBranch(BranchInst inst) {
        if (inst.isConditional()) {
            // === 条件跳转: br %cond, label %true, label %false ===
            BasicBlock trueTarget = (BasicBlock) inst.getOperand(1);
            BasicBlock falseTarget = (BasicBlock) inst.getOperand(2);
            String trueLabel = makeLabel(trueTarget);
            String falseLabel = makeLabel(falseTarget);

            // [Phase 2 Fix] 构建 CFG (Liveness 分析依赖)
            MipsBasicBlock trueMips = blockMap.get(trueTarget);
            MipsBasicBlock falseMips = blockMap.get(falseTarget);
            linkBlocks(currentMipsBlock, trueMips);
            linkBlocks(currentMipsBlock, falseMips);

            // 1. 预执行 True 分支的 Phi Copy
            processPhiNodes(trueTarget);
            // 2. 预执行 False 分支的 Phi Copy
            processPhiNodes(falseTarget);

            // 3. 生成跳转指令
            // [Phase 2 重构] 使用 getOperand 加载条件值
            Operand cond = getOperand(inst.getCondition());
            currentMipsBlock.addInstruction(new MipsBranch("bnez", cond, trueLabel));
            currentMipsBlock.addInstruction(new MipsBranch("j", falseLabel));

        } else {
            // === 无条件跳转: br label %target ===
            BasicBlock target = (BasicBlock) inst.getOperand(0);

            // [Phase 2 Fix] 构建 CFG
            MipsBasicBlock targetMips = blockMap.get(target);
            linkBlocks(currentMipsBlock, targetMips);

            // 1. 处理 Phi
            processPhiNodes(target);

            // 2. 跳转
            String label = makeLabel(target);
            currentMipsBlock.addInstruction(new MipsBranch("j", label));
        }
    }

    // [Phase 2 Fix] 辅助方法：连接两个块
    private void linkBlocks(MipsBasicBlock pred, MipsBasicBlock succ) {
        if (pred != null && succ != null) {
            pred.addSuccessor(succ);
            succ.addPredecessor(pred);
        }
    }

    // === 新增：函数调用翻译 ===
    private void translateCall(CallInst inst) {
        Function targetFunc = inst.getFunction();
        List<Value> args = inst.getArguments();
        String funcLabel = targetFunc.getName().substring(1); // 去掉 @

        // [优化] 内联库函数调用
        if (isInlineableLibFunction(funcLabel)) {
            inlineLibFunction(funcLabel, inst);
            return;
        }

        // 1. 准备前 4 个参数 (寄存器传递)
        for (int i = 0; i < Math.min(4, args.size()); i++) {
            Value arg = args.get(i);
            Operand argOp = getOperand(arg);
            MipsRegister argReg = MipsRegister.values()[MipsRegister.A0.getId() + i];
            currentMipsBlock.addInstruction(new MipsMove(argReg, argOp));
        }

        // 2. 准备栈参数 (第 5 个及以后)
        // [核心修改] 不再移动 SP，而是直接存入预留的栈底空间
        for (int i = 4; i < args.size(); i++) {
            Value arg = args.get(i);
            Operand argOp = getOperand(arg);
            
            // 计算在预留区中的偏移：(i - 4) * 4
            // 这个区域已经被 StackManager 预留好了，绝对安全
            int offset = (i - 4) * 4;
            
            currentMipsBlock.addInstruction(new MipsLoadStore(
                MipsLoadStore.Type.SW, argOp, MipsRegister.SP, offset));
        }

        // 3. 生成跳转指令
        currentMipsBlock.addInstruction(new MipsBranch("jal", funcLabel));

        // [核心修改] 不需要恢复栈指针 (addu)，因为我们根本没动它

        // 4. 处理返回值
        if (!inst.getType().isVoidType()) {
            // [Phase 2 重构] 返回值在 $v0，存入 valueMap
            VirtualRegister dest = new VirtualRegister();
            currentMipsBlock.addInstruction(new MipsMove(dest, MipsRegister.V0));
            valueMap.put(inst, dest);
        }
    }

    /**
     * 判断是否为可内联的库函数
     */
    private boolean isInlineableLibFunction(String funcName) {
        return funcName.equals("getint") || funcName.equals("getch") ||
               funcName.equals("putint") || funcName.equals("putch") ||
               funcName.equals("putstr");
    }

    /**
     * 内联库函数：直接生成 syscall 指令序列
     */
    private void inlineLibFunction(String funcName, CallInst inst) {
        int syscallCode = switch (funcName) {
            case "getint" -> 5;
            case "getch" -> 12;
            case "putint" -> 1;
            case "putch" -> 11;
            case "putstr" -> 4;
            default -> throw new RuntimeException("Unknown lib function: " + funcName);
        };

        // 对于 putint/putch/putstr，需要加载参数到 $a0
        if (funcName.equals("putint") || funcName.equals("putch") || funcName.equals("putstr")) {
            List<Value> args = inst.getArguments();
            if (!args.isEmpty()) {
                // [Phase 2 重构] 使用 getOperand 加载参数
                Operand argOp = getOperand(args.get(0));
                currentMipsBlock.addInstruction(new MipsMove(MipsRegister.A0, argOp));
            }
        }

        // 生成 syscall
        currentMipsBlock.addInstruction(new MipsLi(MipsRegister.V0, syscallCode));
        currentMipsBlock.addInstruction(new MipsSyscall());

        // 对于 getint/getch，返回值在 $v0，需要保存
        if (funcName.equals("getint") || funcName.equals("getch")) {
            if (!inst.getType().isVoidType()) {
                // [Phase 2 重构] 返回值存入 valueMap
                VirtualRegister dest = new VirtualRegister();
                currentMipsBlock.addInstruction(new MipsMove(dest, MipsRegister.V0));
                valueMap.put(inst, dest);
            }
        }
    }

    // === 新增：数组地址计算翻译 ===
    private void translateGep(GetElementPtrInst inst) {
        // [Phase 2 重构] GEP 是地址计算，结果是地址（VReg）
        Value base = inst.getBasePtr();
        List<Value> indices = inst.getIndices();

        // 1. 获取基地址
        Operand baseOp = getOperand(base);

        // 2. 计算偏移量
        Value indexValue = indices.size() == 1 ? indices.get(0) : indices.get(indices.size() - 1);

        // 3. 计算最终地址
        if (indexValue instanceof ConstantInt constIdx) {
            int offsetBytes = constIdx.getValue() * 4;
            if (offsetBytes == 0) {
                // 无偏移，直接复用基地址
                valueMap.put(inst, baseOp);
                return;
            } else {
                // addiu dest, base, imm
                VirtualRegister dest = new VirtualRegister();
                currentMipsBlock.addInstruction(new MipsBinary("addiu", dest, baseOp, offsetBytes));
                valueMap.put(inst, dest);
            }
        } else {
            // 变量索引
            Operand indexOp = getOperand(indexValue);
            VirtualRegister shiftedIdx = new VirtualRegister();
            VirtualRegister dest = new VirtualRegister();
            // sll shiftedIdx, indexOp, 2 (乘以4)
            currentMipsBlock.addInstruction(new MipsBinary("sll", shiftedIdx, indexOp, 2));
            // addu dest, baseOp, shiftedIdx
            currentMipsBlock.addInstruction(new MipsBinary("addu", dest, baseOp, shiftedIdx));
            valueMap.put(inst, dest);
        }
    }

    // 处理 Zext (零扩展) 和 Trunc (截断)
    // 在目前的 MIPS 实现中，i1 和 i32 在寄存器中存储方式相同 (0/1)，
    // 所以只需要把源操作数的值读取出来，存入目标指令的栈位置即可。
    private void translateZext(Instruction inst) {
        // [Phase 2 重构] Zext/Trunc 在 MIPS 中是 no-op，直接传递 VReg
        Operand src = getOperand(inst.getOperand(0));
        // 直接复用同一个 VReg （后续分配器会处理）
        valueMap.put(inst, src);
    }

    // === 核心辅助方法 ===

    /**
     * Phi 降级逻辑 (Parallel Copy):
     * Phi 节点的值通过 Move 指令传递到目标 VReg，不再使用栈
     */
    private void processPhiNodes(BasicBlock targetBlock) {
        for (Instruction inst : targetBlock.getInstructions()) {
            if (!(inst instanceof PhiInst phi)) {
                break; // Phi 都在开头
            }

            // 查找当前 Block 在 Phi 中对应的 incoming value
            Value incomingValue = null;
            for (int i = 0; i < phi.getOperands().size(); i += 2) {
                Value val = phi.getOperand(i);
                BasicBlock blk = (BasicBlock) phi.getOperand(i + 1);

                if (blk == this.currentLLVMBlock) {
                    incomingValue = val;
                    break;
                }
            }

            if (incomingValue != null) {
                // [修正] 使用 computeIfAbsent 获取/创建 Phi 的目标 VReg
                // Phi 指令本身对应一个 VReg，所有前驱块都 Move 到这个 VReg
                Operand src = getOperand(incomingValue);
                Operand dest = valueMap.computeIfAbsent(phi, k -> new VirtualRegister());
                
                // 生成 Move 指令完成 Parallel Copy
                currentMipsBlock.addInstruction(new MipsMove(dest, src));
            }
        }
    }

    /**
     * [Phase 2 重构] 获取 Value 对应的 Operand
     * 优先从 valueMap 查找，如果不存在则根据类型创建/加载
     */
    private Operand getOperand(Value value) {
        // 1. 常量处理
        if (value instanceof ConstantInt constantInt) {
            int val = constantInt.getValue();
            // [优化] 常量 0 直接使用 $zero 寄存器，避免分配冲突
            if (val == 0) {
                return MipsRegister.ZERO;
            }
            VirtualRegister vreg = new VirtualRegister();
            currentMipsBlock.addInstruction(new MipsLi(vreg, val));
            return vreg;
        }
        
        // 2. 全局变量：加载地址到新 VReg
        if (value instanceof GlobalVariable globalVar) {
            String label = globalVar.getName().substring(1);
            VirtualRegister vreg = new VirtualRegister();
            currentMipsBlock.addInstruction(new MipsLa(vreg, label));
            return vreg;
        }
        
        // 3. AllocaInst：加载栈地址到新 VReg
        if (value instanceof AllocaInst) {
            int offset = stackManager.getOffset(value);
            VirtualRegister vreg = new VirtualRegister();
            currentMipsBlock.addInstruction(new MipsBinary("addiu", vreg, MipsRegister.SP, offset));
            return vreg;
        }
        
        // 4. 其他指令结果：从 valueMap 获取
        if (valueMap.containsKey(value)) {
            return valueMap.get(value);
        }
        
        // 5. Fallback: 可能是 Argument，从栈加载
        // (Arguments 在 StackManager 中有栈槽)
        // [Optimization] Check Local Cache First (Reset per Block)
        if (currentMipsBlock != lastVisitedBlock) {
            localArgCache.clear();
            lastVisitedBlock = currentMipsBlock;
        }
        if (localArgCache.containsKey(value)) {
            return localArgCache.get(value);
        }

        try {
            int offset = stackManager.getOffset(value);
            VirtualRegister vreg = new VirtualRegister();
            currentMipsBlock.addInstruction(new MipsLoadStore(
                MipsLoadStore.Type.LW, vreg, MipsRegister.SP, offset));
            
            // [Fix] Do NOT cache Argument VReg GLOBALLY! 
            // Caching here causes Def-Use dominance issues.
            // But caching LOCALLY within a block is safe and efficient.
            localArgCache.put(value, vreg);
            
            return vreg;
        } catch (RuntimeException e) {
            throw new RuntimeException("getOperand: Cannot resolve value: " + value + 
                " (type: " + value.getClass().getSimpleName() + ")", e);
        }
    }

    // [已删除] loadValueToRegister 和 saveRegisterToStack
    // 这些使用硬编码物理寄存器的方法已被 getOperand + VirtualRegister 替代

    private String makeLabel(BasicBlock bb) {
        return bb.getParentFunction().getName().substring(1) + "_" + bb.getName();
    }
}