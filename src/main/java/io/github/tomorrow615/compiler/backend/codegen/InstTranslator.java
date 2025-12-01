package io.github.tomorrow615.compiler.backend.codegen;

import io.github.tomorrow615.compiler.backend.mips.MipsRegister;
import io.github.tomorrow615.compiler.backend.mips.assembly.*;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsBasicBlock;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.List;

/**
 * 指令翻译器
 * 负责将 LLVM Instruction 翻译为 MIPS Instruction
 * 核心策略：栈式分配 + Phi降级(Copy-in-Predecessor)
 */
public class InstTranslator {
    private final StackManager stackManager;
    private final MipsFunction mipsFunction;

    // 当前正在写入的 MIPS 基本块
    private MipsBasicBlock currentMipsBlock;
    // 当前正在处理的 LLVM 基本块 (用于 Phi 节点的前驱判断)
    private BasicBlock currentLLVMBlock;

    public InstTranslator(StackManager stackManager, MipsFunction mipsFunction) {
        this.stackManager = stackManager;
        this.mipsFunction = mipsFunction;
    }

    /**
     * 翻译整个函数的入口
     */
    public void translate(Function llvmFunction) {
        for (BasicBlock llvmBB : llvmFunction.getBasicBlocks()) {
            this.currentLLVMBlock = llvmBB;

            // 1. 创建对应的 MipsBasicBlock
            String label = makeLabel(llvmBB);
            MipsBasicBlock mipsBB = new MipsBasicBlock(label);
            mipsFunction.addBasicBlock(mipsBB);
            this.currentMipsBlock = mipsBB;

            // 2. 翻译块内的每条指令
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
        // 1. 如果有返回值，加载到 $v0
        if (!inst.isVoidRet()) {
            loadValueToRegister(inst.getReturnValue(), MipsRegister.V0);
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
        loadValueToRegister(inst.getLhs(), MipsRegister.T0);
        loadValueToRegister(inst.getRhs(), MipsRegister.T1);

        String op = switch (inst.getOp()) {
            case ADD -> "addu";
            case SUB -> "subu";
            case MUL -> "mul";
            case SDIV -> "div";
            case SREM -> "div";
            default -> "addu";
        };

        if (inst.getOp() == BinaryOpInst.OpCode.SDIV) {
            // div rs, rt
            currentMipsBlock.addInstruction(new MipsBinary("div", MipsRegister.T0, MipsRegister.T1));
            // mflo rd
            currentMipsBlock.addInstruction(new MipsBinary("mflo", MipsRegister.T2));
        } else if (inst.getOp() == BinaryOpInst.OpCode.SREM) {
            // div rs, rt
            currentMipsBlock.addInstruction(new MipsBinary("div", MipsRegister.T0, MipsRegister.T1));
            // mfhi rd
            currentMipsBlock.addInstruction(new MipsBinary("mfhi", MipsRegister.T2));
        } else {
            // add, sub, mul
            currentMipsBlock.addInstruction(new MipsBinary(op, MipsRegister.T2, MipsRegister.T0, MipsRegister.T1));
        }

        saveRegisterToStack(MipsRegister.T2, inst);
    }

    private void translateLoad(LoadInst inst) {
        loadValueToRegister(inst.getPointer(), MipsRegister.T0);
        currentMipsBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.LW, MipsRegister.T1, MipsRegister.T0, 0));
        saveRegisterToStack(MipsRegister.T1, inst);
    }

    private void translateStore(StoreInst inst) {
        loadValueToRegister(inst.getValue(), MipsRegister.T0);
        loadValueToRegister(inst.getPointer(), MipsRegister.T1);
        currentMipsBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.SW, MipsRegister.T0, MipsRegister.T1, 0));
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
        saveRegisterToStack(MipsRegister.T2, inst);
    }

    private void translateBranch(BranchInst inst) {
        if (inst.isConditional()) {
            // === 条件跳转: br %cond, label %true, label %false ===
            BasicBlock trueTarget = (BasicBlock) inst.getOperand(1);
            BasicBlock falseTarget = (BasicBlock) inst.getOperand(2);
            String trueLabel = makeLabel(trueTarget);
            String falseLabel = makeLabel(falseTarget);

            // 1. 预执行 True 分支的 Phi Copy
            processPhiNodes(trueTarget);
            // 2. 预执行 False 分支的 Phi Copy
            processPhiNodes(falseTarget);

            // 3. 生成跳转指令
            loadValueToRegister(inst.getOperand(0), MipsRegister.T0);
            currentMipsBlock.addInstruction(new MipsBranch("bnez", MipsRegister.T0, trueLabel)); // 使用新增的构造函数
            currentMipsBlock.addInstruction(new MipsBranch("j", falseLabel));

        } else {
            // === 无条件跳转: br label %target ===
            BasicBlock target = (BasicBlock) inst.getOperand(0);

            // 1. 处理 Phi
            processPhiNodes(target);

            // 2. 跳转
            String label = makeLabel(target);
            currentMipsBlock.addInstruction(new MipsBranch("j", label));
        }
    }

    // === 新增：函数调用翻译 ===
    private void translateCall(CallInst inst) {
        Function targetFunc = inst.getFunction();
        List<Value> args = inst.getArguments();

        // 1. 准备参数
        for (int i = 0; i < args.size(); i++) {
            Value arg = args.get(i);

            if (i < 4) {
                // 前4个参数：放入 $a0 - $a3
                MipsRegister argReg = MipsRegister.values()[MipsRegister.A0.getId() + i];
                loadValueToRegister(arg, argReg);
            } else {
                // 后续参数：存入栈底预留区域 (Outgoing Args)
                // 此时 $sp 指向当前函数的栈底，Outgoing Args 区域就在 0($sp), 4($sp)...
                // 先加载到临时寄存器 $t0
                loadValueToRegister(arg, MipsRegister.T0);
                // 再存入栈: sw $t0, offset($sp)
                // 偏移量 = i * 4
                currentMipsBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.SW, MipsRegister.T0, MipsRegister.SP, i * 4));
            }
        }

        // 2. 生成跳转指令: jal func_name
        String funcLabel = targetFunc.getName().substring(1); // 去掉 @
        // SysY 库函数名兼容 (getint, putint 等)
        if (funcLabel.equals("getint") || funcLabel.equals("putint") ||
                funcLabel.equals("putch") || funcLabel.equals("putstr")) {
            // 库函数不需要特殊处理，直接 jal 名字即可，但在链接时需注意
            // 这里假设生成的汇编最后会和库函数代码拼在一起，或者模拟器支持
        }

        currentMipsBlock.addInstruction(new MipsBranch("jal", funcLabel));

        // 3. 处理返回值
        if (!inst.getType().isVoidType()) {
            // 返回值在 $v0，存回栈上分配给 CallInst 的位置
            saveRegisterToStack(MipsRegister.V0, inst);
        }
    }

    // === 新增：数组地址计算翻译 ===
    private void translateGep(GetElementPtrInst inst) {
        // GEP 格式: %ptr = getelementptr type, base, idx0, idx1...
        // SysY 中只有一维数组，常见形式：
        // 1. 访问局部/全局数组: gep [10 x i32]*, base, 0, idx  -> base + idx * 4
        // 2. 访问指针参数:      gep i32*, base, idx           -> base + idx * 4

        Value base = inst.getBasePtr();
        List<Value> indices = inst.getIndices();

        // 1. 加载基地址到 $t0
        // loadValueToRegister 会自动处理：
        // - 如果是 Alloca (局部数组)，加载的是栈地址 (addiu $t0, $sp, off)
        // - 如果是 GlobalVariable (全局数组)，加载的是标签地址 (la $t0, label)
        // - 如果是 Pointer Argument，加载的是存着的地址值 (lw $t0, off)
        loadValueToRegister(base, MipsRegister.T0);

        // 2. 计算偏移量
        // SysY 场景下，我们要找那个“非零”的索引，或者最后一个索引
        // Case A: gep base, 0, idx (数组解引用 + 索引)
        // Case B: gep base, idx    (指针索引)

        Value indexValue = null;

        if (indices.size() == 1) {
            indexValue = indices.get(0);
        } else {
            // 多个索引，通常第一个是 0，最后一个是实际索引
            // 简单起见，我们取最后一个。SysY 不支持多维数组，所以这样通常是安全的
            indexValue = indices.get(indices.size() - 1);
        }

        // 3. 计算最终地址
        if (indexValue instanceof ConstantInt constIdx) {
            // 优化：如果是常数索引
            int offsetBytes = constIdx.getValue() * 4;
            if (offsetBytes == 0) {
                // 无偏移，直接将基地址 $t0 存入结果
                saveRegisterToStack(MipsRegister.T0, inst);
            } else {
                // addiu $t2, $t0, imm
                currentMipsBlock.addInstruction(new MipsBinary("addiu", MipsRegister.T2, MipsRegister.T0, offsetBytes));
                saveRegisterToStack(MipsRegister.T2, inst);
            }
        } else {
            // 变量索引
            // 加载索引到 $t1
            loadValueToRegister(indexValue, MipsRegister.T1);
            // 计算字节偏移: $t1 = $t1 * 4 (使用 sll)
            // sll $t1, $t1, 2
            currentMipsBlock.addInstruction(new MipsBinary("sll", MipsRegister.T1, MipsRegister.T1, 2));
            // 基地址 + 偏移: addu $t2, $t0, $t1
            currentMipsBlock.addInstruction(new MipsBinary("addu", MipsRegister.T2, MipsRegister.T0, MipsRegister.T1));
            // 存入结果
            saveRegisterToStack(MipsRegister.T2, inst);
        }
    }

    // 处理 Zext (零扩展) 和 Trunc (截断)
    // 在目前的 MIPS 实现中，i1 和 i32 在寄存器中存储方式相同 (0/1)，
    // 所以只需要把源操作数的值读取出来，存入目标指令的栈位置即可。
    private void translateZext(Instruction inst) {
        // Zext/Trunc: %dest = zext %src to type
        Value src = inst.getOperand(0);

        // 1. 加载源操作数到 $t0
        loadValueToRegister(src, MipsRegister.T0);

        // 2. 将 $t0 存入本指令 (%dest) 在栈上的位置
        saveRegisterToStack(MipsRegister.T0, inst);
    }

    // === 核心辅助方法 ===

    /**
     * Phi 降级逻辑:
     * 检查 targetBlock 开头是否有 Phi 指令，如果有，将当前块流向它的值写入栈
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
                // 生成 Copy 代码: lw $t0, offset(incoming) -> sw $t0, offset(phi)
                loadValueToRegister(incomingValue, MipsRegister.T0);
                saveRegisterToStack(MipsRegister.T0, phi);
            }
        }
    }

    private void loadValueToRegister(Value value, MipsRegister targetReg) {
        if (value instanceof ConstantInt constantInt) {
            currentMipsBlock.addInstruction(new MipsLi(targetReg, constantInt.getValue()));
        } else if (value instanceof GlobalVariable globalVar) {
            String label = globalVar.getName().substring(1);
            currentMipsBlock.addInstruction(new MipsLa(targetReg, label));
        } else if (value instanceof AllocaInst) {
            int offset = stackManager.getOffset(value);
            currentMipsBlock.addInstruction(new MipsBinary("addiu", targetReg, MipsRegister.SP, offset));
        } else {
            int offset = stackManager.getOffset(value);
            currentMipsBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.LW, targetReg, MipsRegister.SP, offset));
        }
    }

    private void saveRegisterToStack(MipsRegister srcReg, Value destValue) {
        int offset = stackManager.getOffset(destValue);
        currentMipsBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.SW, srcReg, MipsRegister.SP, offset));
    }

    private String makeLabel(BasicBlock bb) {
        return bb.getParentFunction().getName().substring(1) + "_" + bb.getName();
    }
}