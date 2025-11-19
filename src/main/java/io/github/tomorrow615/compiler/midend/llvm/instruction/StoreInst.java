package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.VoidType;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.*;

public class StoreInst extends Instruction {
    public StoreInst(Value value, Value pointer, BasicBlock parentBlock) {
        super(VoidType.get(), parentBlock);
        this.addOperand(value);
        this.addOperand(pointer);
    }

    public Value getValue() {
        return this.getOperand(0);
    }

    public Value getPointer() {
        return this.getOperand(1);
    }

    @Override
    public String toString(SlotTracker tracker) {
        Value val = getValue();
        Value ptr = getPointer();
        return "store " + val.getType().toString() + " " + tracker.getName(val) + ", "
                + ptr.getType().toString() + " " + tracker.getName(ptr);
    }

    @Override
    public String toString() {
        return "StoreInst@" + hashCode();
    }
}
