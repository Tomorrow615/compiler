package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.VoidType;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.*;

public class BranchInst extends Instruction {

    /**
     * 构造 'br label <target>' (无条件跳转)
     * @param target 目标基本块
     * @param parentBlock 插入到的基本块
     */
    public BranchInst(BasicBlock target, BasicBlock parentBlock) {
        // br 指令没有返回值 (void) 且没有名字
        super(VoidType.get(), parentBlock);
        // 操作数0: 目标块
        this.addOperand(target);
    }

    /**
     * 构造 'br i1 <cond>, label <trueTarget>, label <falseTarget>' (有条件跳转)
     * @param cond 条件 (必须是 i1 类型)
     * @param trueTarget 'true' 分支的目标基本块
     * @param falseTarget 'false' 分支的目标基本块
     * @param parentBlock 插入到的基本块
     */
    public BranchInst(Value cond, BasicBlock trueTarget, BasicBlock falseTarget, BasicBlock parentBlock) {
        super(VoidType.get(), parentBlock);
        // 操作数0: 条件
        this.addOperand(cond);
        // 操作数1: true 目标块
        this.addOperand(trueTarget);
        // 操作数2: false 目标块
        this.addOperand(falseTarget);
    }

    public boolean isConditional() {
        // 有3个操作数 (cond, true, false) 的是有条件跳转
        return this.operands.size() == 3;
    }

    @Override
    public String toString(SlotTracker tracker) {
        if (isConditional()) {
            Value cond = getOperand(0);
            return "br " + cond.getType().toString() + " " + tracker.getName(cond) + ", "
                    + "label %" + tracker.getName(getOperand(1)) + ", "
                    + "label %" + tracker.getName(getOperand(2));
        } else {
            return "br label %" + tracker.getName(getOperand(0));
        }
    }

    @Override
    public String toString() {
        return "BranchInst<" + (isConditional() ? "cond" : "uncond") + ">@" + hashCode();
    }
}
