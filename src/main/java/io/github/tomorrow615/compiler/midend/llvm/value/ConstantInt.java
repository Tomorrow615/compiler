package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.IntegerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;

public class ConstantInt extends Constant {

    private final int value;

    // 构造 i32 常量
    public ConstantInt(int value) {
        super(IntegerType.i32); // 默认为 i32
        this.value = value;
    }

    // 构造 i1 常量 (布尔值)
    public ConstantInt(boolean boolValue) {
        super(IntegerType.i1); // 默认为 i1
        this.value = boolValue ? 1 : 0;
    }

    // 构造指定位宽的常量 (通用)
    public ConstantInt(int value, IntegerType type) {
        super(type);
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String toString() {
        // 常量在 IR 中只打印它们的值
        return String.valueOf(this.value);
    }

    @Override
    public String getName() {
        // 常量没有 %name，它们的名字就是它们的值
        return this.toString();
    }
}
