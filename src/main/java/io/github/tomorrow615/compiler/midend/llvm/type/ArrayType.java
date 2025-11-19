package io.github.tomorrow615.compiler.midend.llvm.type;

public class ArrayType extends Type {
    private final int numElements;
    private final Type elementType;

    public ArrayType(int numElements, Type elementType) {
        this.numElements = numElements;
        this.elementType = elementType;
    }

    public int getNumElements() {
        return numElements;
    }

    public Type getElementType() {
        return elementType;
    }

    @Override
    public boolean isArrayType() {
        return true;
    }

    @Override
    public String toString() {
        return "[" + numElements + " x " + elementType.toString() + "]";
    }
}
