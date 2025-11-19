package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.*;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.*;

public class ReturnInst extends Instruction {
    public ReturnInst(BasicBlock parentBlock) {
        super(VoidType.get(), parentBlock);
    }

    public ReturnInst(Value value, BasicBlock parentBlock) {
        super(VoidType.get(), parentBlock);
        this.addOperand(value);
    }

    public boolean isVoidRet() {
        return this.operands.isEmpty();
    }

    public Value getReturnValue() {
        if (isVoidRet()) {
            return null;
        }
        return this.getOperand(0);
    }

    @Override
    public String toString(SlotTracker tracker) {
        if (isVoidRet()) {
            return "ret void";
        } else {
            Value retVal = getReturnValue();
            return "ret " + retVal.getType().toString() + " " + tracker.getName(retVal);
        }
    }

    @Override
    public String toString() {
        return "ReturnInst<" + (isVoidRet() ? "void" : "value") + ">@" + hashCode(); // 调试用
    }
}
