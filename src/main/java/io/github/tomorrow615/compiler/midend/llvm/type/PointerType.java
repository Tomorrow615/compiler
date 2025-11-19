package io.github.tomorrow615.compiler.midend.llvm.type;

public class PointerType extends Type {
    private final Type targetType;

    public PointerType(Type targetType) {
        this.targetType = targetType;
    }

    public Type getTargetType() {
        return targetType;
    }

    @Override
    public boolean isPointerType() {
        return true;
    }

    @Override
    public String toString() {
        return targetType.toString() + "*";
    }
}
