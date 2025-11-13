package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.util.*;

public class GlobalVariable extends GlobalValue {

    private Constant initializer; // 全局变量的初始值 (可选)

    public GlobalVariable(Type type, String name, Constant initializer) {
        // 全局变量本身是一个指针，所以它的类型是指针类型
        super(new PointerType(type), name);
        this.initializer = initializer;
    }

    public Constant getInitializer() {
        return initializer;
    }

    @Override
    public String toString(SlotTracker tracker) {
        // 全局变量不依赖 tracker, 但为统一接口
        StringBuilder sb = new StringBuilder();
        sb.append(this.getName()).append(" = dso_local global "); // [cite: 1518-1525] (已更新 dso_local)

        Type targetType = ((PointerType) this.type).getTargetType();
        sb.append(targetType.toString());

        if (initializer != null) {
            // 假设常量也不需要 tracker
            sb.append(" ").append(initializer.toString(tracker));
        } else {
            sb.append(" 0");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "GlobalVariable<" + this.name + ">@" + hashCode();
    }
}