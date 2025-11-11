package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;

public abstract class GlobalValue extends Constant {

    public GlobalValue(Type type) {
        super(type);
    }

    public GlobalValue(Type type, String name) {
        super(type, name);
    }
}
