package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.util.*;

public class Argument extends Value {

    private final Function parentFunction;
    private final int index; // 参数索引

    public Argument(Type type, String name, Function parentFunction, int index) {
        super(type, name);
        this.parentFunction = parentFunction;
        this.index = index;
    }

    public Function getParentFunction() {
        return parentFunction;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public String toString(SlotTracker tracker) {
        return tracker.getName(this);
    }

    @Override
    public String toString() {
        return "Argument<" + this.name + ">@" + hashCode();
    }
}
