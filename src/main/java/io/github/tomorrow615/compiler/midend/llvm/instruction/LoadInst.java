package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.*;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.*;

public class LoadInst extends Instruction {
    public LoadInst(Value pointer, String name, BasicBlock parentBlock) {
        super(((PointerType) pointer.getType()).getTargetType(), name, parentBlock);
        this.addOperand(pointer);
    }

    public Value getPointer() {
        return this.getOperand(0);
    }

    @Override
    public String toString(SlotTracker tracker) {
        Value ptr = getPointer();
        return tracker.getName(this) + " = load " + this.getType().toString() + ", "
                + ptr.getType().toString() + " " + tracker.getName(ptr);
    }

    @Override
    public String toString() {
        return "LoadInst<" + this.name + ">@" + hashCode();
    }
}
