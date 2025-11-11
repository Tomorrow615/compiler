package io.github.tomorrow615.compiler.midend.llvm.type;

public class PointerType extends Type {

    private final Type targetType; // 指针指向的类型

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
        // 例如: i32*
        return targetType.toString() + "*";
    }
}
