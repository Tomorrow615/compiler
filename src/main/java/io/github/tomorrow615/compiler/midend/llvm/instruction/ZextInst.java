package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.*;

public class ZextInst extends Instruction {
    private final Type targetType;

    public ZextInst(Value value, Type targetType, String name, BasicBlock parentBlock) {
        super(targetType, name, parentBlock);
        this.targetType = targetType;
        this.addOperand(value);
    }

    public Value getValueToExt() {
        return this.getOperand(0);
    }

    @Override
    public String toString(SlotTracker tracker) {
        Value val = getValueToExt();
        return tracker.getName(this) + " = zext " + val.getType().toString() + " " + tracker.getName(val)
                + " to " + this.targetType.toString();
    }

    @Override
    public String toString() {
        return "ZextInst<" + this.name + ">@" + hashCode();
    }
}
