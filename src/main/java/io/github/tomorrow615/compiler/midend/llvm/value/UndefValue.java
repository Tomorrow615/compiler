package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.util.SlotTracker;

/**
 * Undef Value - 未定义的值
 * 用于 SSA 构建中表示未初始化的变量
 */
public class UndefValue extends Constant {
    private static final java.util.Map<Type, UndefValue> instances = new java.util.HashMap<>();
    
    private UndefValue(Type type) {
        super(type, "undef");
    }
    
    /**
     * 获取指定类型的 UndefValue 单例
     */
    public static UndefValue get(Type type) {
        return instances.computeIfAbsent(type, UndefValue::new);
    }
    
    @Override
    public String toString(SlotTracker tracker) {
        return "undef";
    }
    
    @Override
    public String toString() {
        return "undef";
    }
}
