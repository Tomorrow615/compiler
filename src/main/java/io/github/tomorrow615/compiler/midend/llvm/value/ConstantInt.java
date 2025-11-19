package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.IntegerType;
import io.github.tomorrow615.compiler.util.*;

public class ConstantInt extends Constant {
    private final int value;

    public ConstantInt(int value) {
        super(IntegerType.i32);
        this.value = value;
    }

    public ConstantInt(boolean boolValue) {
        super(IntegerType.i1);
        this.value = boolValue ? 1 : 0;
    }

    public ConstantInt(int value, IntegerType type) {
        super(type);
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String toString(SlotTracker tracker) {
        return String.valueOf(this.value);
    }

    @Override
    public String toString() {
        return String.valueOf(this.value);
    }
}
