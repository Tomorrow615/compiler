package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.util.SlotTracker;

public class ConstantString extends Constant {
    private final String value;

    public ConstantString(Type type, String value) {
        super(type);
        this.value = value;
    }

    @Override
    public String toString(SlotTracker tracker) {
        String llvmString = value.replace("\n", "\\0A") + "\\00";
        return "c\"" + llvmString + "\"";
    }

    @Override
    public String toString() {
        return "ConstantString<" + value + ">@" + hashCode();
    }
}