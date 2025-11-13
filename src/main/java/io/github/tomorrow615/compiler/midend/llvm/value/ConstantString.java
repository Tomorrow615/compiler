package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.util.SlotTracker;

public class ConstantString extends Constant {

    private final String value; // 存储原始 Java 字符串 (e.g., "Hello\n")


    public ConstantString(Type type, String value) {
        super(type);
        this.value = value;
    }

    @Override
    public String toString(SlotTracker tracker) {
        // 负责将 "Hello\n" 转换为 c"Hello\0A\00"
        // (逻辑从 getGlobalString 移到这里)
        String llvmString = value.replace("\n", "\\0A") + "\\00";
        return "c\"" + llvmString + "\"";
    }

    @Override
    public String toString() {
        return "ConstantString<" + value + ">@" + hashCode();
    }
}