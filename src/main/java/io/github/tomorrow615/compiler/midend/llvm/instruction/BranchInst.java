package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.VoidType;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.*;

public class BranchInst extends Instruction {
    public BranchInst(BasicBlock target, BasicBlock parentBlock) {
        super(VoidType.get(), parentBlock);
        this.addOperand(target);
        // 维护 CFG 关系
        if (parentBlock != null) {
            parentBlock.addSuccessor(target);
            target.addPredecessor(parentBlock);
        }
    }

    public BranchInst(Value cond, BasicBlock trueTarget, BasicBlock falseTarget, BasicBlock parentBlock) {
        super(VoidType.get(), parentBlock);
        this.addOperand(cond);
        this.addOperand(trueTarget);
        this.addOperand(falseTarget);
        // 维护 CFG 关系
        if (parentBlock != null) {
            parentBlock.addSuccessor(trueTarget);
            trueTarget.addPredecessor(parentBlock);
            parentBlock.addSuccessor(falseTarget);
            falseTarget.addPredecessor(parentBlock);
        }
    }

    public boolean isConditional() {
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
