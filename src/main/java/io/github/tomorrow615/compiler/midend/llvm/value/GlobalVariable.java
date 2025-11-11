package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;

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
    public String toString() {
        // e.g., @g = global i32 0
        // e.g., @.str = private unnamed_addr constant [13 x i8] c"Hello World\00"

        StringBuilder sb = new StringBuilder();
        sb.append(this.getName()).append(" = ");
        // (简化) 我们暂时只支持简单的 global
        sb.append("global ");

        // 类型是指针，我们需要它指向的类型
        Type targetType = ((PointerType) this.type).getTargetType();
        sb.append(targetType.toString());

        if (initializer != null) {
            sb.append(" ").append(initializer.toString());
        } else {
            // SysY 的全局变量如果未初始化，默认为 0
            sb.append(" 0");
        }
        return sb.toString();
    }
}