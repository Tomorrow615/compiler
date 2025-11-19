package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.util.*;

public class AllocaInst extends Instruction {
    private final Type allocatedType;

    public AllocaInst(Type type, String name, BasicBlock parentBlock) {
        super(new PointerType(type), name, parentBlock);
        this.allocatedType = type;
    }

    public AllocaInst(Type type, String name) {
        super(new PointerType(type), name);
        this.allocatedType = type;
    }

    public Type getAllocatedType() {
        return allocatedType;
    }

    @Override
    public String toString(SlotTracker tracker) {
        return tracker.getName(this) + " = alloca " + this.allocatedType.toString();
    }

    @Override
    public String toString() {
        return "AllocaInst<" + this.name + ">@" + hashCode();
    }
}
