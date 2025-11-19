package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.util.*;

import java.util.List;
import java.util.ArrayList;

public abstract class Value {
    protected final Type type;
    protected String name;

    protected final List<Use> users; // 谁在使用我

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

    public abstract String toString(SlotTracker tracker);

    @Override
    public String toString() {
        return "Value<" + type + ", name=" + name + ">@" + hashCode();
    }
}
