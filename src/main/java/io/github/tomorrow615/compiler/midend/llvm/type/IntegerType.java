package io.github.tomorrow615.compiler.midend.llvm.type;

public class IntegerType extends Type {

    private final int bitWidth;

    public static final IntegerType i32 = new IntegerType(32);
    public static final IntegerType i8 = new IntegerType(8);
    public static final IntegerType i1 = new IntegerType(1); // 用于布尔值和 icmp

    private IntegerType(int bitWidth) {
        this.bitWidth = bitWidth;
    }

    public int getBitWidth() {
        return bitWidth;
    }

    @Override
    public boolean isIntegerType() {
        return true;
    }

    @Override
    public String toString() {
        return "i" + bitWidth;
    }
}
