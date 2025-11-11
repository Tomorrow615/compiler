package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;

import java.util.List;
import java.util.ArrayList;

public abstract class Value {
    protected final Type type;
    protected String name;

    protected final List<Use> users; // 记录所有 "Use" (使用) 了这个 Value 的 Use 对象

    public Value(Type type) {
        this.type = type;
        this.name = "";
        this.users = new ArrayList<>();
    }

    public Value(Type type, String name) {
        this.type = type;
        this.name = name;
        this.users = new ArrayList<>();
    }

    public Type getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addUse(Use use) {
        if (!this.users.contains(use)) {
            this.users.add(use);
        }
    }

    public void removeUse(Use use) {
        this.users.remove(use);
    }

    @Override
    public abstract String toString();
}
