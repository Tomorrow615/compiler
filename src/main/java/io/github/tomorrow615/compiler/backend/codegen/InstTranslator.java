package io.github.tomorrow615.compiler.backend.codegen;

import io.github.tomorrow615.compiler.backend.mips.MipsRegister;
import io.github.tomorrow615.compiler.backend.mips.assembly.*;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsBasicBlock;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;
import io.github.tomorrow615.compiler.midend.llvm.type.IntegerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;

import io.github.tomorrow615.compiler.backend.mips.operand.Operand;
import io.github.tomorrow615.compiler.backend.mips.operand.VirtualRegister;
import io.github.tomorrow615.compiler.midend.optimize.MagicNumber;
import io.github.tomorrow615.compiler.util.Config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    
    private final Map<Value, Operand> valueMap;
    private final Map<BasicBlock, MipsBasicBlock> blockMap;

    // [Optimization] Local Cache for Argument Loads within a block
    private final Map<Value, VirtualRegister> localArgCache;
    private MipsBasicBlock lastVisitedBlock;

    // [Optimization] 为了在 Basic Block 范围内复用全局变量地址
    private final Map<String, VirtualRegister> globalAddrCache = new HashMap<>();

    // [Optimization] 记录被融合（Folded）的指令，跳过代码生成
    private final Set<Instruction> foldedInstructions = new HashSet<>();

    // [Optimization] Syscall $v0 状态缓存：避免重复 li $v0, X
    private int cachedV0 = -1;

    public InstTranslator(StackManager stackManager, MipsFunction mipsFunction) {
        this.stackManager = stackManager;
        this.mipsFunction = mipsFunction;
        this.valueMap = new HashMap<>();
        this.blockMap = new HashMap<>();
        this.localArgCache = new HashMap<>();
        this.lastVisitedBlock = null;
    }

    public void translate(Function llvmFunction) {
        for (BasicBlock llvmBB : llvmFunction.getBasicBlocks()) {
            String label = makeLabel(llvmBB);
            MipsBasicBlock mipsBB = new MipsBasicBlock(label);
            mipsFunction.addBasicBlock(mipsBB);
            blockMap.put(llvmBB, mipsBB);
        }

        for (BasicBlock llvmBB : llvmFunction.getBasicBlocks()) {
            this.currentLLVMBlock = llvmBB;
            this.currentMipsBlock = blockMap.get(llvmBB);
            
            // [Optimization] 清除本块内的地址/参数缓存
            globalAddrCache.clear();
            localArgCache.clear();
            foldedInstructions.clear();
            cachedV0 = -1; // 清除 syscall $v0 缓存

            // 1. 预扫描：标记哪些 GEP 可以被跳过 (GEP Fusion)
            for (Instruction inst : llvmBB.getInstructions()) {
                if ((inst instanceof LoadInst load) && canFoldGep(load.getPointer())) {
                    foldedInstructions.add((Instruction) load.getPointer());
                } else if ((inst instanceof StoreInst store) && canFoldGep(store.getPointer())) {
                    foldedInstructions.add((Instruction) store.getPointer());
                }
            }

            for (Instruction inst : llvmBB.getInstructions()) {
                if (inst instanceof PhiInst phi) {
                    valueMap.computeIfAbsent(phi, k -> new VirtualRegister());
                } else {
                    break;
                }
            }

            for (Instruction inst : llvmBB.getInstructions()) {
                // 如果是被折叠的指令，直接跳过生成
                if (foldedInstructions.contains(inst)) {
                    continue;
                }
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
            // No-op
        } else if (inst instanceof IcmpInst icmp) {
            translateIcmp(icmp);
        } else if (inst instanceof BranchInst br) {
            translateBranch(br);
        } else if (inst instanceof PhiInst) {
            // Handled by predecessors
        } else if (inst instanceof CallInst call) {
            translateCall(call);
        } else if (inst instanceof GetElementPtrInst gep) {
            translateGep(gep);
        } else if (inst instanceof ZextInst zext) {
            translateZext(zext);
        } else if (inst instanceof TruncInst trunc) {
            translateZext(trunc);
        }
    }

    private void translateReturn(ReturnInst inst) {
        if (!inst.isVoidRet()) {
            Operand retVal = getOperand(inst.getReturnValue());
            currentMipsBlock.addInstruction(new MipsMove(MipsRegister.V0, retVal));
        }

        // [Phase 2 Optimization] main 函数不需要恢复 $ra
        if (Config.ENABLE_MAIN_NO_STACK && mipsFunction.getName().equals("main")) {
            // Do nothing for main if optimization enabled
        } else {
            int raOffset = stackManager.getRaOffset();
            currentMipsBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.LW, MipsRegister.RA, MipsRegister.SP, raOffset));
        }

        int frameSize = stackManager.getFrameSize();
        if (frameSize > 0) {
            currentMipsBlock.addInstruction(new MipsBinary("addu", MipsRegister.SP, MipsRegister.SP, frameSize));
        }

        if (mipsFunction.getName().equals("main")) {
            currentMipsBlock.addInstruction(new MipsLi(MipsRegister.V0, 10));
            currentMipsBlock.addInstruction(new MipsSyscall());
        } else {
            currentMipsBlock.addInstruction(new MipsBranch("jr", MipsRegister.RA));
        }
    }

    private void translateBinary(BinaryOpInst inst) {
        VirtualRegister dest = getOrCreateDestReg(inst);

        // [Phase 1 Optimization] 尝试立即数优化
        if (Config.ENABLE_BACKEND_IMM_OPT && tryImmediateOptimization(inst, dest)) {
            return;
        }

        Operand lhs = getOperand(inst.getLhs());
        Operand rhs = getOperand(inst.getRhs());

        if (inst.getOp() == BinaryOpInst.OpCode.SDIV && inst.getRhs() instanceof ConstantInt constDiv) {
            int divisor = constDiv.getValue();
            if (divisor > 1 && !isPowerOfTwo(divisor)) {
                if (translateDivByMagicNumber(inst, lhs, divisor)) return;
            }
        }

        if (inst.getOp() == BinaryOpInst.OpCode.SDIV) {
            currentMipsBlock.addInstruction(new MipsBinary("div", lhs, rhs));
            currentMipsBlock.addInstruction(new MipsBinary("mflo", dest));
        } else if (inst.getOp() == BinaryOpInst.OpCode.SREM) {
            currentMipsBlock.addInstruction(new MipsBinary("div", lhs, rhs));
            currentMipsBlock.addInstruction(new MipsBinary("mfhi", dest));
        } else if (inst.getOp() == BinaryOpInst.OpCode.SHL) {
            // 左移：如果移位量是常数 (0-31)，直接生成 sll 立即数指令
            if (inst.getRhs() instanceof ConstantInt shiftAmt && isShiftImm5(shiftAmt.getValue())) {
                Operand lhsOp = getOperand(inst.getLhs());
                currentMipsBlock.addInstruction(new MipsBinary("sll", dest, lhsOp, shiftAmt.getValue()));
            } else {
                currentMipsBlock.addInstruction(new MipsBinary("sllv", dest, lhs, rhs));
            }
        } else if (inst.getOp() == BinaryOpInst.OpCode.ASHR) {
            // 算术右移：如果移位量是常数 (0-31)，直接生成 sra 立即数指令
            if (inst.getRhs() instanceof ConstantInt shiftAmt && isShiftImm5(shiftAmt.getValue())) {
                Operand lhsOp = getOperand(inst.getLhs());
                currentMipsBlock.addInstruction(new MipsBinary("sra", dest, lhsOp, shiftAmt.getValue()));
            } else {
                currentMipsBlock.addInstruction(new MipsBinary("srav", dest, lhs, rhs));
            }
        } else if (inst.getOp() == BinaryOpInst.OpCode.LSHR) {
            // 逻辑右移：如果移位量是常数 (0-31)，直接生成 srl 立即数指令
            if (inst.getRhs() instanceof ConstantInt shiftAmt && isShiftImm5(shiftAmt.getValue())) {
                Operand lhsOp = getOperand(inst.getLhs());
                currentMipsBlock.addInstruction(new MipsBinary("srl", dest, lhsOp, shiftAmt.getValue()));
            } else {
                currentMipsBlock.addInstruction(new MipsBinary("srlv", dest, lhs, rhs));
            }
        } else {
            String op = switch (inst.getOp()) {
                case ADD -> "addu";
                case SUB -> "subu";
                case MUL -> "mul";
                case AND -> "and";
                case OR -> "or";
                case XOR -> "xor";
                default -> "addu";
            };
            currentMipsBlock.addInstruction(new MipsBinary(op, dest, lhs, rhs));
        }
    }

    /**
     * [Phase 1] 立即数优化
     * 针对 ADD/SUB/AND/OR/XOR，如果右操作数是小常数，直接生成 I-Type 指令
     * 
     * @return true 如果成功应用优化，false 则回退到通用路径
     */
    private boolean tryImmediateOptimization(BinaryOpInst inst, VirtualRegister dest) {
        Value lhsVal = inst.getLhs();
        Value rhsVal = inst.getRhs();

        // 只处理右操作数是常数的情况
        if (!(rhsVal instanceof ConstantInt constInt)) {
            return false;
        }

        int immVal = constInt.getValue();
        BinaryOpInst.OpCode opCode = inst.getOp();

        switch (opCode) {
            case ADD -> {
                // addiu: 16位有符号立即数 [-32768, 32767]
                if (isSignedImm16(immVal)) {
                    Operand lhs = getOperand(lhsVal);
                    currentMipsBlock.addInstruction(new MipsBinary("addiu", dest, lhs, immVal));
                    return true;
                }
            }
            case SUB -> {
                // sub r1, r2, const -> addiu r1, r2, -const
                // 注意: Integer.MIN_VALUE 取反会溢出，必须排除
                if (immVal != Integer.MIN_VALUE) {
                    int negImm = -immVal;
                    if (isSignedImm16(negImm)) {
                        Operand lhs = getOperand(lhsVal);
                        currentMipsBlock.addInstruction(new MipsBinary("addiu", dest, lhs, negImm));
                        return true;
                    }
                }
            }
            case AND -> {
                // andi: 16位无符号立即数 [0, 65535]
                if (isUnsignedImm16(immVal)) {
                    Operand lhs = getOperand(lhsVal);
                    currentMipsBlock.addInstruction(new MipsBinary("andi", dest, lhs, immVal));
                    return true;
                }
            }
            case OR -> {
                // ori: 16位无符号立即数 [0, 65535]
                if (isUnsignedImm16(immVal)) {
                    Operand lhs = getOperand(lhsVal);
                    currentMipsBlock.addInstruction(new MipsBinary("ori", dest, lhs, immVal));
                    return true;
                }
            }
            case XOR -> {
                // xori: 16位无符号立即数 [0, 65535]
                if (isUnsignedImm16(immVal)) {
                    Operand lhs = getOperand(lhsVal);
                    currentMipsBlock.addInstruction(new MipsBinary("xori", dest, lhs, immVal));
                    return true;
                }
            }
            default -> {
                return false;
            }
        }
        return false;
    }

    /**
     * 检查值是否在 16位有符号整数范围内 [-32768, 32767]
     */
    private boolean isSignedImm16(int val) {
        return val >= -32768 && val <= 32767;
    }

    /**
     * 检查值是否在 16位无符号整数范围内 [0, 65535]
     */
    private boolean isUnsignedImm16(int val) {
        return val >= 0 && val <= 65535;
    }

    /**
     * 检查值是否在移位立即数范围内 [0, 31]
     */
    private boolean isShiftImm5(int val) {
        return val >= 0 && val <= 31;
    }

    private boolean translateDivByMagicNumber(BinaryOpInst inst, Operand lhsOp, int divisor) {
        MagicNumber.MagicResult magic = MagicNumber.computeSigned(divisor);
        if (magic == null) return false;

        VirtualRegister vLhs = new VirtualRegister();
        VirtualRegister vMagic = new VirtualRegister();
        VirtualRegister vHi = new VirtualRegister();
        VirtualRegister vShifted = new VirtualRegister();
        VirtualRegister vSign = new VirtualRegister();
        VirtualRegister dest = getOrCreateDestReg(inst);
        
        currentMipsBlock.addInstruction(new MipsMove(vLhs, lhsOp));
        currentMipsBlock.addInstruction(new MipsLi(vMagic, magic.multiplier));
        currentMipsBlock.addInstruction(new MipsBinary("mult", vLhs, vMagic));
        currentMipsBlock.addInstruction(new MipsBinary("mfhi", vHi));
        
        if (magic.needsAdd) {
            currentMipsBlock.addInstruction(new MipsBinary("addu", vHi, vHi, vLhs));
        }
        
        if (magic.shift > 0) {
            currentMipsBlock.addInstruction(new MipsBinary("sra", vShifted, vHi, magic.shift));
        } else {
            currentMipsBlock.addInstruction(new MipsMove(vShifted, vHi));
        }
        
        currentMipsBlock.addInstruction(new MipsBinary("sra", vSign, vLhs, 31));
        currentMipsBlock.addInstruction(new MipsBinary("subu", dest, vShifted, vSign));
        
        return true;
    }

    private boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    private void translateLoad(LoadInst inst) {
        Value ptrVal = inst.getPointer();
        Operand ptrOp;
        int offset = 0;

        if (ptrVal instanceof GetElementPtrInst gep && isSimpleConstantGep(gep)) {
            ptrOp = getOperand(gep.getBasePtr());
            offset = getGepConstantOffset(gep);
        } else {
            ptrOp = getOperand(ptrVal);
        }

        VirtualRegister dest = getOrCreateDestReg(inst);
        currentMipsBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.LW, dest, ptrOp, offset));
    }

    private void translateStore(StoreInst inst) {
        Operand value = getOperand(inst.getValue());
        Value ptrVal = inst.getPointer();
        Operand ptrOp;
        int offset = 0;

        if (ptrVal instanceof GetElementPtrInst gep && isSimpleConstantGep(gep)) {
            ptrOp = getOperand(gep.getBasePtr());
            offset = getGepConstantOffset(gep);
        } else {
            ptrOp = getOperand(ptrVal);
        }

        currentMipsBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.SW, value, ptrOp, offset));
    }

    // 辅助判断方法：检查 GEP 是否可以被折叠
    private boolean canFoldGep(Value pointer) {
        if (pointer instanceof GetElementPtrInst gep) {
            return isSimpleConstantGep(gep);
        }
        return false;
    }

    // 检查是否为简单的常量偏移 GEP
    // 安全策略：
    // 1. 单索引 GEP (一维数组或指针偏移): gep %ptr, %const -> offset = const * 4
    // 2. 双索引 GEP (二维数组/全局数组衰退): gep @arr, 0, %const -> offset = const * 4
    // 其他情况保守处理，不融合
    private boolean isSimpleConstantGep(GetElementPtrInst gep) {
        List<Value> indices = gep.getIndices();
        if (indices.isEmpty()) return false;
        
        if (indices.size() == 1) {
            return indices.get(0) instanceof ConstantInt;
        } else if (indices.size() == 2) {
            // 必须保证第一维是 0，才不会产生额外的偏移
            return (indices.get(0) instanceof ConstantInt c1 && c1.getValue() == 0) &&
                   (indices.get(1) instanceof ConstantInt);
        }
        
        return false;
    }

    private int getGepConstantOffset(GetElementPtrInst gep) {
        List<Value> indices = gep.getIndices();
        ConstantInt lastIdx = (ConstantInt) indices.get(indices.size() - 1);
        return lastIdx.getValue() * 4;
    }

    // ... (skipped some methods)

    private void translateZext(Instruction inst) {
        // Zext/Trunc 从语义上是位宽转换，但在 MIPS 寄存器中都是 32 位存储
        // SysY 中只有 i1/i8/i32。
        
        // [Safety Fix] 对于 Trunc 到 i1 的情况，必须清除高位
        // 防止例如 3 (..11) 被当做 true，但某些指令只看最低位导致错误
        if (inst instanceof TruncInst && inst.getType() instanceof IntegerType it && it.getBitWidth() == 1) {
            Operand src = getOperand(inst.getOperand(0));
            VirtualRegister dest = getOrCreateDestReg(inst);
            currentMipsBlock.addInstruction(new MipsBinary("andi", dest, src, 1));
            return;
        }
        
        // 其他情况 (Zext i1->i32, Trunc i32->i8等整个字处理) 直接复用寄存器
        Operand src = getOperand(inst.getOperand(0));
        // 检查是否已经有预分配的寄存器
        Operand existing = valueMap.get(inst);
        if (existing instanceof VirtualRegister destVr) {
            // 需要复制到预分配的寄存器
            currentMipsBlock.addInstruction(new MipsMove(destVr, src));
        } else {
            valueMap.put(inst, src);
        }
    }

    private void translateIcmp(IcmpInst inst) {
        Operand lhs = getOperand(inst.getLhs());
        Operand rhs = getOperand(inst.getRhs());
        VirtualRegister dest = getOrCreateDestReg(inst);

        String op = switch (inst.getCmpType()) {
            case EQ -> "seq";
            case NE -> "sne";
            case SLT -> "slt";
            case SLE -> "sle";
            case SGT -> "sgt";
            case SGE -> "sge";
        };

        currentMipsBlock.addInstruction(new MipsCompare(op, dest, lhs, rhs));
    }

    private void translateBranch(BranchInst inst) {
        if (inst.isConditional()) {
            Value condVal = inst.getCondition();
            BasicBlock trueTarget = (BasicBlock) inst.getOperand(1);
            BasicBlock falseTarget = (BasicBlock) inst.getOperand(2);
            String trueLabel = makeLabel(trueTarget);
            String falseLabel = makeLabel(falseTarget);

            MipsBasicBlock trueMips = blockMap.get(trueTarget);
            MipsBasicBlock falseMips = blockMap.get(falseTarget);
            linkBlocks(currentMipsBlock, trueMips);
            linkBlocks(currentMipsBlock, falseMips);

            processPhiNodes(trueTarget);
            processPhiNodes(falseTarget);

            // [Optimization] 尝试穿透 Zext/Trunc 找到原始的 Icmp
            // 循环穿透直到找到 IcmpInst 或无法穿透为止
            while (condVal instanceof ZextInst || condVal instanceof TruncInst) {
                 if (condVal instanceof Instruction zt && zt.getOperand(0) instanceof Instruction inner) {
                     condVal = inner;
                 } else {
                     break;
                 }
            }

            if (condVal instanceof IcmpInst icmp) {
                Operand lhs = getOperand(icmp.getLhs());
                Operand rhs = getOperand(icmp.getRhs());
                String branchOp = switch (icmp.getCmpType()) {
                    case EQ -> "beq";
                    case NE -> "bne";
                    case SLT -> "blt";
                    case SLE -> "ble";
                    case SGT -> "bgt";
                    case SGE -> "bge";
                };
                currentMipsBlock.addInstruction(new MipsBranch(branchOp, lhs, rhs, trueLabel));
                currentMipsBlock.addInstruction(new MipsBranch("j", falseLabel));
            } else {
                Operand cond = getOperand(inst.getCondition());
                currentMipsBlock.addInstruction(new MipsBranch("bnez", cond, trueLabel));
                currentMipsBlock.addInstruction(new MipsBranch("j", falseLabel));
            }
        } else {
            BasicBlock target = (BasicBlock) inst.getOperand(0);
            MipsBasicBlock targetMips = blockMap.get(target);
            linkBlocks(currentMipsBlock, targetMips);
            processPhiNodes(target);
            String label = makeLabel(target);
            currentMipsBlock.addInstruction(new MipsBranch("j", label));
        }
    }

    private void linkBlocks(MipsBasicBlock pred, MipsBasicBlock succ) {
        if (pred != null && succ != null) {
            pred.addSuccessor(succ);
            succ.addPredecessor(pred);
        }
    }

    private void translateCall(CallInst inst) {
        Function targetFunc = inst.getFunction();
        List<Value> args = inst.getArguments();
        String funcLabel = targetFunc.getName().substring(1);

        if (isInlineableLibFunction(funcLabel)) {
            inlineLibFunction(funcLabel, inst);
            return;
        }

        for (int i = 0; i < Math.min(4, args.size()); i++) {
            Value arg = args.get(i);
            Operand argOp = getOperand(arg);
            MipsRegister argReg = MipsRegister.values()[MipsRegister.A0.getId() + i];
            currentMipsBlock.addInstruction(new MipsMove(argReg, argOp));
        }

        for (int i = 4; i < args.size(); i++) {
            Value arg = args.get(i);
            Operand argOp = getOperand(arg);
            int offset = (i - 4) * 4;
            currentMipsBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.SW, argOp, MipsRegister.SP, offset));
        }

        currentMipsBlock.addInstruction(new MipsBranch("jal", funcLabel));
        cachedV0 = -1; // 函数调用可能修改 $v0，清除缓存

        if (!inst.getType().isVoidType()) {
            VirtualRegister dest = getOrCreateDestReg(inst);
            currentMipsBlock.addInstruction(new MipsMove(dest, MipsRegister.V0));
        }
    }

    private boolean isInlineableLibFunction(String funcName) {
        return funcName.equals("getint") || funcName.equals("getch") ||
               funcName.equals("putint") || funcName.equals("putch") ||
               funcName.equals("putstr");
    }

    private void inlineLibFunction(String funcName, CallInst inst) {
        int syscallCode = switch (funcName) {
            case "getint" -> 5;
            case "getch" -> 12;
            case "putint" -> 1;
            case "putch" -> 11;
            case "putstr" -> 4;
            default -> 10;
        };

        if (funcName.equals("putint") || funcName.equals("putch") || funcName.equals("putstr")) {
            List<Value> args = inst.getArguments();
            if (!args.isEmpty()) {
                Operand argOp = getOperand(args.get(0));
                currentMipsBlock.addInstruction(new MipsMove(MipsRegister.A0, argOp));
            }
        }

        // [Optimization] 只有当 $v0 的值不是目标 syscallCode 时才生成 li 指令
        if (cachedV0 != syscallCode) {
            currentMipsBlock.addInstruction(new MipsLi(MipsRegister.V0, syscallCode));
            cachedV0 = syscallCode;
        }
        currentMipsBlock.addInstruction(new MipsSyscall());

        if (funcName.equals("getint") || funcName.equals("getch")) {
            // 【关键】getint/getch 的 syscall 会将返回值写入 $v0，
            // 因此 $v0 不再是 syscallCode，必须清除缓存！
            cachedV0 = -1;
            if (!inst.getType().isVoidType()) {
                VirtualRegister dest = getOrCreateDestReg(inst);
                currentMipsBlock.addInstruction(new MipsMove(dest, MipsRegister.V0));
            }
        }
    }

    private void translateGep(GetElementPtrInst inst) {
        Value base = inst.getBasePtr();
        List<Value> indices = inst.getIndices();
        Operand baseOp = getOperand(base);
        Value indexValue = indices.size() == 1 ? indices.get(0) : indices.get(indices.size() - 1);

        if (indexValue instanceof ConstantInt constIdx) {
            int offsetBytes = constIdx.getValue() * 4;
            if (offsetBytes == 0) {
                // 检查是否已经有预分配的寄存器
                Operand existing = valueMap.get(inst);
                if (existing instanceof VirtualRegister destVr) {
                    currentMipsBlock.addInstruction(new MipsMove(destVr, baseOp));
                } else {
                    valueMap.put(inst, baseOp);
                }
            } else {
                VirtualRegister dest = getOrCreateDestReg(inst);
                currentMipsBlock.addInstruction(new MipsBinary("addiu", dest, baseOp, offsetBytes));
            }
        } else {
            Operand indexOp = getOperand(indexValue);
            VirtualRegister shiftedIdx = new VirtualRegister();
            VirtualRegister dest = getOrCreateDestReg(inst);
            currentMipsBlock.addInstruction(new MipsBinary("sll", shiftedIdx, indexOp, 2));
            currentMipsBlock.addInstruction(new MipsBinary("addu", dest, baseOp, shiftedIdx));
        }
    }


    private void processPhiNodes(BasicBlock targetBlock) {
        java.util.List<Operand[]> copies = new java.util.ArrayList<>();
        for (Instruction inst : targetBlock.getInstructions()) {
            if (!(inst instanceof PhiInst phi)) break;
            Value incomingValue = null;
            for (int i = 0; i < phi.getOperands().size(); i += 2) {
                if (((BasicBlock) phi.getOperand(i + 1)) == this.currentLLVMBlock) {
                    incomingValue = phi.getOperand(i);
                    break;
                }
            }
            if (incomingValue != null) {
                Operand src = getOperand(incomingValue);
                Operand dest = valueMap.computeIfAbsent(phi, k -> new VirtualRegister());
                if (!src.equals(dest)) copies.add(new Operand[]{src, dest});
            }
        }
        
        java.util.Map<Operand, Operand> srcMap = new java.util.HashMap<>();
        java.util.Set<Operand> pending = new java.util.HashSet<>();
        for (Operand[] copy : copies) {
            srcMap.put(copy[1], copy[0]);
            pending.add(copy[1]);
        }

        while (!pending.isEmpty()) {
            Operand safeToEmit = null;
            for (Operand dest : pending) {
                boolean isBlocked = false;
                for (Operand otherDest : pending) {
                    if (srcMap.get(otherDest).equals(dest)) {
                        isBlocked = true;
                        break;
                    }
                }
                if (!isBlocked) {
                    safeToEmit = dest;
                    break;
                }
            }

            if (safeToEmit != null) {
                currentMipsBlock.addInstruction(new MipsMove(safeToEmit, srcMap.get(safeToEmit)));
                pending.remove(safeToEmit);
            } else {
                Operand cycleNode = pending.iterator().next();
                VirtualRegister temp = new VirtualRegister();
                currentMipsBlock.addInstruction(new MipsMove(temp, cycleNode));
                Operand current = cycleNode;
                while (true) {
                    Operand src = srcMap.get(current);
                    if (src.equals(cycleNode)) {
                        currentMipsBlock.addInstruction(new MipsMove(current, temp));
                        pending.remove(current);
                        break;
                    } else {
                        currentMipsBlock.addInstruction(new MipsMove(current, src));
                        pending.remove(current);
                        current = src;
                    }
                }
            }
        }
    }

    private Operand getOperand(Value value) {
        if (value instanceof ConstantInt constantInt) {
            int val = constantInt.getValue();
            if (val == 0) return MipsRegister.ZERO;
            VirtualRegister vreg = new VirtualRegister();
            currentMipsBlock.addInstruction(new MipsLi(vreg, val));
            return vreg;
        }
        
        if (value instanceof GlobalValue global) {
            String name = global.getName().substring(1);
            if (globalAddrCache.containsKey(name)) return globalAddrCache.get(name);
            
            VirtualRegister vreg = new VirtualRegister();
            currentMipsBlock.addInstruction(new MipsLa(vreg, name));
            
            // [Optimization] 限制缓存大小，防止寄存器溢出 (Spill)
            // 如果缓存超过 12 个，不再缓存，直接退化为每次生成 la
            if (globalAddrCache.size() < 12) {
                globalAddrCache.put(name, vreg);
            }
            return vreg;
        }

        if (value instanceof AllocaInst) {
            int offset = stackManager.getOffset(value);
            VirtualRegister vreg = new VirtualRegister();
            currentMipsBlock.addInstruction(new MipsBinary("addiu", vreg, MipsRegister.SP, offset));
            return vreg;
        }

        if (valueMap.containsKey(value)) return valueMap.get(value);

        // [Critical Fix] 处理跨基本块的值引用：
        // 如果一个 Instruction 定义在其他块中，并且还没有被翻译，
        // 我们需要预先为它分配一个虚拟寄存器，后续翻译该指令时会使用这个寄存器。
        if (value instanceof Instruction inst && !(inst instanceof AllocaInst)) {
            VirtualRegister vreg = new VirtualRegister();
            valueMap.put(value, vreg);
            return vreg;
        }

        if (value instanceof Argument arg) {
            if (localArgCache.containsKey(value)) return localArgCache.get(value);
            try {
                int offset = stackManager.getOffset(value);
                VirtualRegister vreg = new VirtualRegister();
                currentMipsBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.LW, vreg, MipsRegister.SP, offset));
                localArgCache.put(value, vreg);
                return vreg;
            } catch (Exception e) {
                // Ignore and throw later
            }
        }
        throw new RuntimeException("getOperand: Cannot resolve value: " + value);
    }

    /**
     * [Critical Fix] 获取或创建指令的目标虚拟寄存器。
     * 如果指令的值之前已经被其他块提前引用并分配了虚拟寄存器，则复用那个寄存器；
     * 否则创建新的虚拟寄存器并记录到 valueMap。
     */
    private VirtualRegister getOrCreateDestReg(Instruction inst) {
        Operand existing = valueMap.get(inst);
        if (existing instanceof VirtualRegister vr) {
            return vr;
        }
        VirtualRegister vreg = new VirtualRegister();
        valueMap.put(inst, vreg);
        return vreg;
    }

    private String makeLabel(BasicBlock bb) {
        return bb.getParentFunction().getName().substring(1) + "_" + bb.getName();
    }
}