package io.github.tomorrow615.compiler.midend.llvm.type;

public class VoidType extends Type {
    private static final VoidType instance = new VoidType();

    private VoidType() {}

    public static VoidType get() {
        return instance;
    }

    @Override
    public boolean isVoidType() {
        return true;
    }

    @Override
    public String toString() {
        return "void";
    }
}
