package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.util.*;

public class GlobalVariable extends GlobalValue {
    private Constant initializer;

    public GlobalVariable(Type type, String name, Constant initializer) {
        super(new PointerType(type), name);
        this.initializer = initializer;
    }

    public Constant getInitializer() {
        return initializer;
    }

    @Override
    public String toString(SlotTracker tracker) {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getName()).append(" = dso_local global ");
        Type targetType = ((PointerType) this.type).getTargetType();
        sb.append(targetType.toString());

        if (initializer != null) {
            sb.append(" ").append(initializer.toString(tracker));
        } else {
            if (targetType.isArrayType()) {
                sb.append(" zeroinitializer");
            } else {
                sb.append(" 0");
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "GlobalVariable<" + this.name + ">@" + hashCode();
    }
}