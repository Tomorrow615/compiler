package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;

public abstract class Constant extends Value {
    public Constant(Type type, String name) {
        super(type, name);
    }

    public Constant(Type type) {
        super(type);
    }
}
