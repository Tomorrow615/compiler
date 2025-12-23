package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;

import java.util.List;
import java.util.ArrayList;

public abstract class User extends Value {
    protected final List<Use> operands;

    public User(Type type) {
        super(type);
        this.operands = new ArrayList<>();
    }

    public User(Type type, String name) {
        super(type, name);
        this.operands = new ArrayList<>();
    }

    public List<Use> getOperands() {
        return operands;
    }

    public Value getOperand(int index) {
        if (index < 0 || index >= operands.size()) {
            throw new IndexOutOfBoundsException("Operand index out of bounds");
        }
        return operands.get(index).getValue();
    }

    public void addOperand(Value value) {
        Use newUse = new Use(this, value);
        this.operands.add(newUse);
    }

    public void setOperand(int index, Value value) {
        if (index < 0 || index >= operands.size()) {
            throw new IndexOutOfBoundsException("Operand index out of bounds");
        }
        this.operands.get(index).setValue(value);
    }

    /**
     * 断开此 User 与所有操作数的引用关系
     * 用于删除指令时清理 Use-Def 链
     */
    public void removeUseFromOperands() {
        for (Use use : operands) {
            use.setValue(null); // 触发 Use.setValue，自动从 Value 的 users 列表中移除
        }
        operands.clear();
    }
}
