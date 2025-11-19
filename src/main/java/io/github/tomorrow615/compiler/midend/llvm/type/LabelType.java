package io.github.tomorrow615.compiler.midend.llvm.type;

public class LabelType extends Type {
    private static final LabelType instance = new LabelType();

    private LabelType() {}

    public static LabelType get() {
        return instance;
    }

    @Override
    public String toString() {
        return "label";
    }
}