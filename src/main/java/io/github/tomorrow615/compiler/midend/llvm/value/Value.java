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

    /**
     * 将所有使用此 Value 的地方替换为新的 Value
     */
    public void replaceAllUsesWith(Value newValue) {
        List<Use> usesCopy = new ArrayList<>(this.users);
        for (Use use : usesCopy) {
            use.setValue(newValue);
        }
    }

    /**
     * 获取所有使用此 Value 的 User 列表
     * 用于优化分析（如死代码删除）
     */
    public List<Use> getUsers() {
        return users;
    }

    public abstract String toString(SlotTracker tracker);

    @Override
    public String toString() {
        return "Value<" + type + ", name=" + name + ">@" + hashCode();
    }
}
