package io.github.tomorrow615.compiler.backend.codegen;

import io.github.tomorrow615.compiler.backend.mips.MipsRegister;
import io.github.tomorrow615.compiler.backend.mips.assembly.*;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsBasicBlock;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;
import io.github.tomorrow615.compiler.midend.optimize.MagicNumber;

import java.util.*;

/**
 * 优化版指令翻译器
 * 相比 InstTranslator，增加了单基本块内的寄存器缓存优化：
 * - 在块内复用已加载的变量值
 * - 延迟写回栈，直到块结束或寄存器被覆盖
 * 
 * 核心策略：保守但正确
 * - 只优化块内变量
 * - 函数调用前强制刷新所有 caller-saved 寄存器
 * - 块结束前刷新所有 dirty 寄存器
 */
public class OptimizedInstTranslator {
    private final StackManager stackManager;
    private final MipsFunction mipsFunction;

    // 当前正在写入的 MIPS 基本块
    private MipsBasicBlock currentMipsBlock;
    // 当前正在处理的 LLVM 基本块
    private BasicBlock currentLLVMBlock;

    // === 寄存器缓存相关 ===
    // 可用于缓存的寄存器池 (使用 $t3-$t7，共5个)
    // 保留 $t0-$t2 给临时计算，避免冲突
    private static final MipsRegister[] CACHE_REGS = {
        MipsRegister.T3, MipsRegister.T4, MipsRegister.T5,
        MipsRegister.T6, MipsRegister.T7
    };

    // 寄存器 -> 当前存储的 Value (null 表示空闲)
    private final Map<MipsRegister, Value> regToValue;
    // Value -> 当前缓存在的寄存器 (null 表示不在寄存器中)
    private final Map<Value, MipsRegister> valueToReg;
    // 哪些寄存器是 dirty 的（值被修改过，需要写回栈）
    private final Set<MipsRegister> dirtyRegs;
    // LRU 顺序：最近使用的寄存器放在末尾
    private final LinkedList<MipsRegister> lruOrder;

    public OptimizedInstTranslator(StackManager stackManager, MipsFunction mipsFunction) {
        this.stackManager = stackManager;
        this.mipsFunction = mipsFunction;
        
        this.regToValue = new HashMap<>();
        this.valueToReg = new HashMap<>();
        this.dirtyRegs = new HashSet<>();
        this.lruOrder = new LinkedList<>();
        
        // 初始化所有缓存寄存器为空闲
        for (MipsRegister reg : CACHE_REGS) {
            regToValue.put(reg, null);
            lruOrder.add(reg);
        }
    }

    /**
     * 翻译整个函数的入口
     */
    public void translate(Function llvmFunction) {
        // 计算可达块 (从 Entry 开始 BFS)
        Set<BasicBlock> reachableBlocks = computeReachableBlocks(llvmFunction);

        for (BasicBlock llvmBB : llvmFunction.getBasicBlocks()) {
            // 跳过不可达块 (Mem2Reg 可能产生悬空引用)
            if (!reachableBlocks.contains(llvmBB)) {
                continue;
            }

            this.currentLLVMBlock = llvmBB;

            // 1. 创建对应的 MipsBasicBlock
            String label = makeLabel(llvmBB);
            MipsBasicBlock mipsBB = new MipsBasicBlock(label);
            mipsFunction.addBasicBlock(mipsBB);
            this.currentMipsBlock = mipsBB;

            // 2. 块开始时清空寄存器缓存状态
            // 因为控制流可能从多个前驱进入，寄存器内容不确定
            clearRegisterCache();

            // 3. 翻译块内的每条指令
            for (Instruction inst : llvmBB.getInstructions()) {
                translateInstruction(inst);
            }

            // 4. 块结束前刷新所有 dirty 寄存器
            // 注意：分支指令已经在 translateBranch 中处理了
        }
    }

    /**
     * 计算从入口块可达的所有基本块 (BFS)
     */
    private Set<BasicBlock> computeReachableBlocks(Function func) {
        Set<BasicBlock> reachable = new HashSet<>();
        if (func.getBasicBlocks().isEmpty()) {
            return reachable;
        }

        Queue<BasicBlock> worklist = new LinkedList<>();
        BasicBlock entry = func.getBasicBlocks().get(0);
        worklist.add(entry);
        reachable.add(entry);

        while (!worklist.isEmpty()) {
            BasicBlock current = worklist.poll();
            for (BasicBlock succ : current.getSuccessors()) {
                if (!reachable.contains(succ)) {
                    reachable.add(succ);
                    worklist.add(succ);
                }
            }
        }
        return reachable;
    }

    /**
     * 清空寄存器缓存状态（不写回，因为块开始时无有效内容）
     */
    private void clearRegisterCache() {
        regToValue.clear();
        valueToReg.clear();
        dirtyRegs.clear();
        lruOrder.clear();
        for (MipsRegister reg : CACHE_REGS) {
            regToValue.put(reg, null);
            lruOrder.add(reg);
        }
    }

    /**
     * 刷新所有 dirty 寄存器到栈
     */
    private void flushAllDirtyRegisters() {
        // 收集需要刷新的寄存器（避免在迭代时修改集合）
        List<MipsRegister> toFlush = new ArrayList<>();
        for (MipsRegister reg : CACHE_REGS) {
            if (dirtyRegs.contains(reg)) {
                toFlush.add(reg);
            }
        }
        
        for (MipsRegister reg : toFlush) {
            Value val = regToValue.get(reg);
            if (val != null && !(val instanceof ConstantInt) && !(val instanceof GlobalVariable)) {
                // 写回栈
                int offset = stackManager.getOffset(val);
                currentMipsBlock.addInstruction(new MipsLoadStore(
                    MipsLoadStore.Type.SW, reg, MipsRegister.SP, offset));
            }
        }
        dirtyRegs.clear();
    }

    /**
     * 使寄存器缓存失效（用于函数调用前）
     * 所有 caller-saved 寄存器需要写回并清空
     */
    private void invalidateCallerSavedRegisters() {
        // 所有 $t 寄存器都是 caller-saved，需要刷新
        flushAllDirtyRegisters();
        // 清空缓存状态，因为调用后这些寄存器内容不确定
        clearRegisterCache();
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

    // === 具体指令翻译 ===

    private void translateReturn(ReturnInst inst) {
        // 刷新所有 dirty 寄存器
        flushAllDirtyRegisters();

        // 1. 如果有返回值，加载到 $v0
        if (!inst.isVoidRet()) {
            loadValueToRegister(inst.getReturnValue(), MipsRegister.V0);
        }

        // 2. 生成函数尾声 (Epilogue)
        int raOffset = stackManager.getRaOffset();
        currentMipsBlock.addInstruction(new MipsLoadStore(
            MipsLoadStore.Type.LW, MipsRegister.RA, MipsRegister.SP, raOffset));

        int frameSize = stackManager.getFrameSize();
        if (frameSize > 0) {
            currentMipsBlock.addInstruction(new MipsBinary(
                "addu", MipsRegister.SP, MipsRegister.SP, frameSize));
        }

        // 3. 返回
        if (mipsFunction.getName().equals("main")) {
            currentMipsBlock.addInstruction(new MipsLi(MipsRegister.V0, 10));
            currentMipsBlock.addInstruction(new MipsSyscall());
        } else {
            currentMipsBlock.addInstruction(new MipsBranch("jr", MipsRegister.RA));
        }
    }

    private void translateBinary(BinaryOpInst inst) {
        // 特殊处理：常量除法优化 (Magic Number)
        if (inst.getOp() == BinaryOpInst.OpCode.SDIV && inst.getRhs() instanceof ConstantInt constDiv) {
            int divisor = constDiv.getValue();
            if (divisor > 1 && !isPowerOfTwo(divisor)) {
                // 使用 Magic Number 优化
                if (translateDivByMagicNumber(inst, divisor)) {
                    return;
                }
            }
        }

        // 加载操作数到临时寄存器 $t0, $t1
        loadValueToRegister(inst.getLhs(), MipsRegister.T0);
        loadValueToRegister(inst.getRhs(), MipsRegister.T1);

        if (inst.getOp() == BinaryOpInst.OpCode.SDIV) {
            currentMipsBlock.addInstruction(new MipsBinary("div", MipsRegister.T0, MipsRegister.T1));
            currentMipsBlock.addInstruction(new MipsBinary("mflo", MipsRegister.T2));
        } else if (inst.getOp() == BinaryOpInst.OpCode.SREM) {
            currentMipsBlock.addInstruction(new MipsBinary("div", MipsRegister.T0, MipsRegister.T1));
            currentMipsBlock.addInstruction(new MipsBinary("mfhi", MipsRegister.T2));
        } else if (inst.getOp() == BinaryOpInst.OpCode.SHL) {
            currentMipsBlock.addInstruction(new MipsBinary("sllv", MipsRegister.T2, MipsRegister.T0, MipsRegister.T1));
        } else if (inst.getOp() == BinaryOpInst.OpCode.ASHR) {
            currentMipsBlock.addInstruction(new MipsBinary("srav", MipsRegister.T2, MipsRegister.T0, MipsRegister.T1));
        } else {
            String op = switch (inst.getOp()) {
                case ADD -> "addu";
                case SUB -> "subu";
                case MUL -> "mul";
                case AND -> "and";
                case OR -> "or";
                default -> "addu";
            };
            currentMipsBlock.addInstruction(new MipsBinary(op, MipsRegister.T2, MipsRegister.T0, MipsRegister.T1));
        }

        // 将结果缓存到寄存器（优化：不立即写回栈）
        cacheValueToRegister(inst, MipsRegister.T2);
    }

    /**
     * 使用 Magic Number 方法翻译常量除法
     * x / d = (x * m) >> (32 + s) + 符号修正
     * 
     * @return true 如果成功优化，false 则回退到普通除法
     */
    private boolean translateDivByMagicNumber(BinaryOpInst inst, int divisor) {
        // 计算 Magic Number
        MagicNumber.MagicResult magic = MagicNumber.computeSigned(divisor);
        if (magic == null) {
            return false;
        }

        // 如果乘数超过 32 位有符号范围，暂不处理（保守策略）
        if (magic.multiplier > Integer.MAX_VALUE || magic.multiplier < Integer.MIN_VALUE) {
            return false;
        }

        // 加载被除数到 $t0
        loadValueToRegister(inst.getLhs(), MipsRegister.T0);

        // 加载 Magic Number 到 $t1
        int m = (int) magic.multiplier;
        currentMipsBlock.addInstruction(new MipsLi(MipsRegister.T1, m));

        // mult $t0, $t1 (有符号乘法)
        currentMipsBlock.addInstruction(new MipsBinary("mult", MipsRegister.T0, MipsRegister.T1));

        // mfhi $t2 (取高 32 位)
        currentMipsBlock.addInstruction(new MipsBinary("mfhi", MipsRegister.T2));

        // 如果需要加法修正 (needsAdd)
        if (magic.needsAdd) {
            // $t2 = $t2 + $t0
            currentMipsBlock.addInstruction(new MipsBinary("addu", MipsRegister.T2, MipsRegister.T2, MipsRegister.T0));
        }

        // 算术右移 shift 位
        if (magic.shift > 0) {
            currentMipsBlock.addInstruction(new MipsBinary("sra", MipsRegister.T2, MipsRegister.T2, magic.shift));
        }

        // 符号修正：如果被除数为负数，结果需要 +1
        // sign = x >> 31 (提取符号位，0 或 -1)
        currentMipsBlock.addInstruction(new MipsBinary("sra", MipsRegister.T1, MipsRegister.T0, 31));
        // result = $t2 - sign (如果 x < 0，sign = -1，所以 result = $t2 + 1)
        currentMipsBlock.addInstruction(new MipsBinary("subu", MipsRegister.T2, MipsRegister.T2, MipsRegister.T1));

        // 将结果缓存到寄存器
        cacheValueToRegister(inst, MipsRegister.T2);
        return true;
    }

    private boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    private void translateLoad(LoadInst inst) {
        // 获取指针地址到 $t0
        loadValueToRegister(inst.getPointer(), MipsRegister.T0);
        // 从内存加载到 $t1
        currentMipsBlock.addInstruction(new MipsLoadStore(
            MipsLoadStore.Type.LW, MipsRegister.T1, MipsRegister.T0, 0));
        // 缓存结果
        cacheValueToRegister(inst, MipsRegister.T1);
    }

    private void translateStore(StoreInst inst) {
        // Store 需要确保值已经写入内存
        // 先检查被存储的值是否在缓存中，如果在则需要刷新
        Value storedValue = inst.getValue();
        Value pointerValue = inst.getPointer();
        
        // 加载要存储的值
        loadValueToRegister(storedValue, MipsRegister.T0);
        // 加载目标地址
        loadValueToRegister(pointerValue, MipsRegister.T1);
        // 执行存储
        currentMipsBlock.addInstruction(new MipsLoadStore(
            MipsLoadStore.Type.SW, MipsRegister.T0, MipsRegister.T1, 0));
        
        // 重要：如果 pointerValue 指向的是一个 AllocaInst（局部变量），
        // 并且这个变量被缓存了，需要使缓存失效，因为内存内容变了
        // 但由于我们的 Load 总是从内存重新读取（通过指针），这里保守处理是安全的
    }

    private void translateIcmp(IcmpInst inst) {
        loadValueToRegister(inst.getLhs(), MipsRegister.T0);
        loadValueToRegister(inst.getRhs(), MipsRegister.T1);

        String op = switch (inst.getCmpType()) {
            case EQ -> "seq";
            case NE -> "sne";
            case SLT -> "slt";
            case SLE -> "sle";
            case SGT -> "sgt";
            case SGE -> "sge";
        };

        currentMipsBlock.addInstruction(new MipsCompare(op, MipsRegister.T2, MipsRegister.T0, MipsRegister.T1));
        cacheValueToRegister(inst, MipsRegister.T2);
    }

    private void translateBranch(BranchInst inst) {
        // 在跳转前，必须刷新所有 dirty 寄存器
        // 因为目标块可能从其他路径到达，不能假设寄存器状态
        flushAllDirtyRegisters();

        if (inst.isConditional()) {
            BasicBlock trueTarget = (BasicBlock) inst.getOperand(1);
            BasicBlock falseTarget = (BasicBlock) inst.getOperand(2);
            String trueLabel = makeLabel(trueTarget);
            String falseLabel = makeLabel(falseTarget);

            // 检测是否需要关键边分割
            // 条件：目标块有 Phi 节点 && 目标块有多个前驱 (这是关键边的定义)
            boolean trueNeedsPhiWork = hasPhiFromCurrentBlock(trueTarget);
            boolean falseNeedsPhiWork = hasPhiFromCurrentBlock(falseTarget);

            if (trueNeedsPhiWork || falseNeedsPhiWork) {
                // 策略：为有 Phi 工作的分支创建"跳板块"(Trampoline Block)
                // 这样每个路径的 Phi 处理代码只有在走该路径时才执行

                // 加载条件
                loadValueToRegister(inst.getOperand(0), MipsRegister.T0);

                if (trueNeedsPhiWork && falseNeedsPhiWork) {
                    // 两个分支都有 Phi 工作，需要创建两个跳板块
                    String trueTrampolineLabel = makeLabel(currentLLVMBlock) + "_to_" + trueTarget.getName();
                    String falseTrampolineLabel = makeLabel(currentLLVMBlock) + "_to_" + falseTarget.getName();

                    // 条件跳转到 true 跳板块
                    currentMipsBlock.addInstruction(new MipsBranch("bnez", MipsRegister.T0, trueTrampolineLabel));
                    // 无条件跳转到 false 跳板块
                    currentMipsBlock.addInstruction(new MipsBranch("j", falseTrampolineLabel));

                    // 创建 true 跳板块
                    MipsBasicBlock trueTrampolineBlock = new MipsBasicBlock(trueTrampolineLabel);
                    mipsFunction.addBasicBlock(trueTrampolineBlock);
                    MipsBasicBlock savedBlock = currentMipsBlock;
                    currentMipsBlock = trueTrampolineBlock;
                    processPhiNodes(trueTarget);
                    currentMipsBlock.addInstruction(new MipsBranch("j", trueLabel));

                    // 创建 false 跳板块
                    MipsBasicBlock falseTrampolineBlock = new MipsBasicBlock(falseTrampolineLabel);
                    mipsFunction.addBasicBlock(falseTrampolineBlock);
                    currentMipsBlock = falseTrampolineBlock;
                    processPhiNodes(falseTarget);
                    currentMipsBlock.addInstruction(new MipsBranch("j", falseLabel));

                    // 恢复 currentMipsBlock
                    currentMipsBlock = savedBlock;

                } else if (trueNeedsPhiWork) {
                    // 只有 true 分支有 Phi
                    String trueTrampolineLabel = makeLabel(currentLLVMBlock) + "_to_" + trueTarget.getName();

                    currentMipsBlock.addInstruction(new MipsBranch("bnez", MipsRegister.T0, trueTrampolineLabel));
                    currentMipsBlock.addInstruction(new MipsBranch("j", falseLabel));

                    MipsBasicBlock trueTrampolineBlock = new MipsBasicBlock(trueTrampolineLabel);
                    mipsFunction.addBasicBlock(trueTrampolineBlock);
                    MipsBasicBlock savedBlock = currentMipsBlock;
                    currentMipsBlock = trueTrampolineBlock;
                    processPhiNodes(trueTarget);
                    currentMipsBlock.addInstruction(new MipsBranch("j", trueLabel));
                    currentMipsBlock = savedBlock;

                } else {
                    // 只有 false 分支有 Phi
                    String falseTrampolineLabel = makeLabel(currentLLVMBlock) + "_to_" + falseTarget.getName();

                    currentMipsBlock.addInstruction(new MipsBranch("bnez", MipsRegister.T0, trueLabel));
                    currentMipsBlock.addInstruction(new MipsBranch("j", falseTrampolineLabel));

                    MipsBasicBlock falseTrampolineBlock = new MipsBasicBlock(falseTrampolineLabel);
                    mipsFunction.addBasicBlock(falseTrampolineBlock);
                    MipsBasicBlock savedBlock = currentMipsBlock;
                    currentMipsBlock = falseTrampolineBlock;
                    processPhiNodes(falseTarget);
                    currentMipsBlock.addInstruction(new MipsBranch("j", falseLabel));
                    currentMipsBlock = savedBlock;
                }
            } else {
                // 没有 Phi 工作，直接跳转
                loadValueToRegister(inst.getOperand(0), MipsRegister.T0);
                currentMipsBlock.addInstruction(new MipsBranch("bnez", MipsRegister.T0, trueLabel));
                currentMipsBlock.addInstruction(new MipsBranch("j", falseLabel));
            }
        } else {
            BasicBlock target = (BasicBlock) inst.getOperand(0);
            processPhiNodes(target);
            String label = makeLabel(target);
            currentMipsBlock.addInstruction(new MipsBranch("j", label));
        }
    }

    /**
     * 检查目标块是否有来自当前块的 Phi 输入
     */
    private boolean hasPhiFromCurrentBlock(BasicBlock targetBlock) {
        for (Instruction inst : targetBlock.getInstructions()) {
            if (!(inst instanceof PhiInst phi)) {
                break;
            }
            for (int i = 0; i < phi.getOperands().size(); i += 2) {
                BasicBlock blk = (BasicBlock) phi.getOperand(i + 1);
                if (blk == this.currentLLVMBlock) {
                    return true;
                }
            }
        }
        return false;
    }

    private void translateCall(CallInst inst) {
        Function targetFunc = inst.getFunction();
        List<Value> args = inst.getArguments();
        String funcLabel = targetFunc.getName().substring(1);

        // [优化] 内联库函数调用
        // 对于内联函数，不需要刷新寄存器缓存，因为 syscall 不会破坏 $t 寄存器
        if (isInlineableLibFunction(funcLabel)) {
            inlineLibFunction(funcLabel, inst);
            return;
        }

        // 函数调用前，刷新所有 caller-saved 寄存器
        invalidateCallerSavedRegisters();

        // 1. 准备参数
        for (int i = 0; i < args.size(); i++) {
            Value arg = args.get(i);
            if (i < 4) {
                MipsRegister argReg = MipsRegister.values()[MipsRegister.A0.getId() + i];
                loadValueToRegister(arg, argReg);
            } else {
                // 超过4个的参数通过栈传递，偏移从0开始 (第5个参数在偏移0)
                loadValueToRegister(arg, MipsRegister.T0);
                currentMipsBlock.addInstruction(new MipsLoadStore(
                    MipsLoadStore.Type.SW, MipsRegister.T0, MipsRegister.SP, (i - 4) * 4));
            }
        }

        // 2. 跳转
        currentMipsBlock.addInstruction(new MipsBranch("jal", funcLabel));

        // 3. 处理返回值
        if (!inst.getType().isVoidType()) {
            // 返回值在 $v0，先 move 到 $t2 然后缓存
            // 因为 $v0 不是缓存寄存器，直接 cacheValueToRegister 会导致错误的 move
            currentMipsBlock.addInstruction(new MipsBinary(
                "addu", MipsRegister.T2, MipsRegister.V0, MipsRegister.ZERO));
            cacheValueToRegister(inst, MipsRegister.T2);
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
                // 加载第一个参数到 $a0
                loadValueToRegister(args.get(0), MipsRegister.A0);
            }
        }

        // 生成 syscall
        currentMipsBlock.addInstruction(new MipsLi(MipsRegister.V0, syscallCode));
        currentMipsBlock.addInstruction(new MipsSyscall());

        // 对于 getint/getch，返回值在 $v0，需要缓存
        if (funcName.equals("getint") || funcName.equals("getch")) {
            if (!inst.getType().isVoidType()) {
                // 将 $v0 移动到 $t2 然后缓存
                currentMipsBlock.addInstruction(new MipsBinary(
                    "addu", MipsRegister.T2, MipsRegister.V0, MipsRegister.ZERO));
                cacheValueToRegister(inst, MipsRegister.T2);
            }
        }
    }

    private void translateGep(GetElementPtrInst inst) {
        Value base = inst.getBasePtr();
        List<Value> indices = inst.getIndices();

        loadValueToRegister(base, MipsRegister.T0);

        Value indexValue = null;
        if (indices.size() == 1) {
            indexValue = indices.get(0);
        } else {
            indexValue = indices.get(indices.size() - 1);
        }

        if (indexValue instanceof ConstantInt constIdx) {
            int offsetBytes = constIdx.getValue() * 4;
            if (offsetBytes == 0) {
                // 无偏移但仍需 move 到 $t2，因为 $t0 可能被后续指令覆盖
                currentMipsBlock.addInstruction(new MipsBinary(
                    "addu", MipsRegister.T2, MipsRegister.T0, MipsRegister.ZERO));
                cacheValueToRegister(inst, MipsRegister.T2);
            } else {
                currentMipsBlock.addInstruction(new MipsBinary(
                    "addiu", MipsRegister.T2, MipsRegister.T0, offsetBytes));
                cacheValueToRegister(inst, MipsRegister.T2);
            }
        } else {
            loadValueToRegister(indexValue, MipsRegister.T1);
            currentMipsBlock.addInstruction(new MipsBinary(
                "sll", MipsRegister.T1, MipsRegister.T1, 2));
            currentMipsBlock.addInstruction(new MipsBinary(
                "addu", MipsRegister.T2, MipsRegister.T0, MipsRegister.T1));
            cacheValueToRegister(inst, MipsRegister.T2);
        }
    }

    private void translateZext(Instruction inst) {
        Value src = inst.getOperand(0);
        loadValueToRegister(src, MipsRegister.T0);
        // move 到 $t2 再缓存，因为 $t0 是临时寄存器可能被覆盖
        currentMipsBlock.addInstruction(new MipsBinary(
            "addu", MipsRegister.T2, MipsRegister.T0, MipsRegister.ZERO));
        cacheValueToRegister(inst, MipsRegister.T2);
    }

    // === Phi 节点处理 (Parallel Copy) ===

    /**
     * 处理 Phi 节点的并行拷贝
     * 
     * 策略：两阶段赋值，避免覆盖问题
     * Phase 1: 读取所有源值到临时位置 (栈槽)
     * Phase 2: 从临时位置写入到目标 Phi 槽位
     * 
     * 这样即使存在 x=y, y=x 的 swap 情况，也能正确处理
     */
    private void processPhiNodes(BasicBlock targetBlock) {
        // 收集所有需要处理的 Phi 及其对应的源值
        List<PhiInst> phis = new ArrayList<>();
        List<Value> incomingValues = new ArrayList<>();

        for (Instruction inst : targetBlock.getInstructions()) {
            if (!(inst instanceof PhiInst phi)) {
                break;
            }

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
                phis.add(phi);
                incomingValues.add(incomingValue);
            }
        }

        if (phis.isEmpty()) {
            return;
        }

        // Phase 1: 读取所有源值到临时寄存器序列 ($s0-$s7 或栈)
        // 为简化实现，我们使用 $t 寄存器的高位区域 + 栈作为临时存储
        // 策略：使用 $s0-$s3 (callee-saved, 但我们在块内使用是安全的)
        // 更保守的做法：全部通过栈中转

        // 临时栈偏移（使用 Outgoing Args 区域，这块空间目前未被使用）
        // 我们使用 SP + 0, SP + 4, SP + 8... 作为临时空间
        // 注意：这依赖于 StackManager 在帧底部预留的 Outgoing Args 空间
        
        // Phase 1: 读取所有源值，暂存到栈的临时区域
        List<Integer> tempOffsets = new ArrayList<>();
        for (int i = 0; i < incomingValues.size(); i++) {
            Value srcVal = incomingValues.get(i);
            int tempOffset = i * 4; // 使用 Outgoing Args 区域
            tempOffsets.add(tempOffset);

            loadValueToRegister(srcVal, MipsRegister.T0);
            currentMipsBlock.addInstruction(new MipsLoadStore(
                MipsLoadStore.Type.SW, MipsRegister.T0, MipsRegister.SP, tempOffset));
        }

        // Phase 2: 从临时区域读取，写入到目标 Phi 槽位
        for (int i = 0; i < phis.size(); i++) {
            PhiInst phi = phis.get(i);
            int tempOffset = tempOffsets.get(i);
            int destOffset = stackManager.getOffset(phi);

            currentMipsBlock.addInstruction(new MipsLoadStore(
                MipsLoadStore.Type.LW, MipsRegister.T0, MipsRegister.SP, tempOffset));
            currentMipsBlock.addInstruction(new MipsLoadStore(
                MipsLoadStore.Type.SW, MipsRegister.T0, MipsRegister.SP, destOffset));
        }
    }

    // === 核心：寄存器缓存辅助方法 ===

    /**
     * 将 Value 加载到指定寄存器
     * 优化：如果 Value 已经在某个缓存寄存器中，直接 move
     */
    private void loadValueToRegister(Value value, MipsRegister targetReg) {
        // Case 1: 常量 - 直接 li
        if (value instanceof ConstantInt constantInt) {
            currentMipsBlock.addInstruction(new MipsLi(targetReg, constantInt.getValue()));
            return;
        }

        // Case 2: 全局变量 - 直接 la
        if (value instanceof GlobalVariable globalVar) {
            String label = globalVar.getName().substring(1);
            currentMipsBlock.addInstruction(new MipsLa(targetReg, label));
            return;
        }

        // Case 3: Alloca - 加载栈地址（不是值）
        if (value instanceof AllocaInst) {
            int offset = stackManager.getOffset(value);
            currentMipsBlock.addInstruction(new MipsBinary(
                "addiu", targetReg, MipsRegister.SP, offset));
            return;
        }

        // Case 4: 检查是否已经在缓存寄存器中
        MipsRegister cachedReg = valueToReg.get(value);
        if (cachedReg != null) {
            // 值已经在缓存寄存器中
            if (cachedReg == targetReg) {
                // 已经在目标寄存器，无需操作
                return;
            }
            // 不在目标寄存器，需要 move
            // 如果目标是临时寄存器 ($t0-$t2)，直接 move
            // 否则可能需要更复杂的处理
            currentMipsBlock.addInstruction(new MipsBinary(
                "addu", targetReg, cachedReg, MipsRegister.ZERO));
            updateLRU(cachedReg);
            return;
        }

        // Case 5: 不在缓存中，从栈加载
        int offset = stackManager.getOffset(value);
        currentMipsBlock.addInstruction(new MipsLoadStore(
            MipsLoadStore.Type.LW, targetReg, MipsRegister.SP, offset));

        // 如果目标寄存器是缓存寄存器之一，更新缓存状态
        // 但通常 targetReg 是 $t0-$t2，不是缓存寄存器
    }

    /**
     * 将寄存器中的值与 Value 关联（用于指令结果）
     * 如果 srcReg 是临时寄存器，需要移动到缓存寄存器
     */
    private void cacheValueToRegister(Value value, MipsRegister srcReg) {
        // 如果是 AllocaInst，其结果是地址而不是值，不需要缓存
        // (AllocaInst 的使用者总是会重新计算地址)
        if (value instanceof AllocaInst) {
            saveRegisterToStack(srcReg, value);
            return;
        }

        // 选择一个缓存寄存器
        MipsRegister cacheReg = allocateCacheRegister();
        
        // 如果 srcReg 和 cacheReg 不同，需要 move
        if (srcReg != cacheReg) {
            currentMipsBlock.addInstruction(new MipsBinary(
                "addu", cacheReg, srcReg, MipsRegister.ZERO));
        }

        // 更新缓存映射
        regToValue.put(cacheReg, value);
        valueToReg.put(value, cacheReg);
        dirtyRegs.add(cacheReg); // 标记为 dirty，需要写回
        updateLRU(cacheReg);
    }

    /**
     * 分配一个缓存寄存器（LRU 策略）
     */
    private MipsRegister allocateCacheRegister() {
        // 优先找空闲寄存器
        for (MipsRegister reg : CACHE_REGS) {
            if (regToValue.get(reg) == null) {
                return reg;
            }
        }

        // 没有空闲寄存器，驱逐 LRU
        MipsRegister victim = lruOrder.removeFirst();
        
        // 如果是 dirty 的，先写回栈
        if (dirtyRegs.contains(victim)) {
            Value oldValue = regToValue.get(victim);
            if (oldValue != null) {
                int offset = stackManager.getOffset(oldValue);
                currentMipsBlock.addInstruction(new MipsLoadStore(
                    MipsLoadStore.Type.SW, victim, MipsRegister.SP, offset));
            }
            dirtyRegs.remove(victim);
        }

        // 清除旧映射
        Value oldValue = regToValue.get(victim);
        if (oldValue != null) {
            valueToReg.remove(oldValue);
        }
        regToValue.put(victim, null);

        lruOrder.addLast(victim);
        return victim;
    }

    /**
     * 更新 LRU 顺序（将寄存器移动到末尾）
     */
    private void updateLRU(MipsRegister reg) {
        lruOrder.remove(reg);
        lruOrder.addLast(reg);
    }

    /**
     * 直接将寄存器保存到栈（不使用缓存）
     */
    private void saveRegisterToStack(MipsRegister srcReg, Value destValue) {
        int offset = stackManager.getOffset(destValue);
        currentMipsBlock.addInstruction(new MipsLoadStore(
            MipsLoadStore.Type.SW, srcReg, MipsRegister.SP, offset));
    }

    private String makeLabel(BasicBlock bb) {
        return bb.getParentFunction().getName().substring(1) + "_" + bb.getName();
    }
}
