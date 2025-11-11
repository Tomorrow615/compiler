package io.github.tomorrow615.compiler.midend.llvm.type;

public abstract class Type {

    public boolean isVoidType() { return false; }
    public boolean isIntegerType() { return false; }
    public boolean isFunctionType() { return false; }
    public boolean isPointerType() { return false; }
    public boolean isArrayType() { return false; }

    @Override
    public abstract String toString();
}