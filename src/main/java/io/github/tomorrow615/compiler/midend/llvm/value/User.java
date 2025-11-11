package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;

import java.util.List;
import java.util.ArrayList;

public abstract class User extends Value {

    // "operands" 列表，通过 Use 对象来管理
    // 这对应了 "User 中的 uselist"
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
        // 创建一个新的 Use 连线，"this" (User) 使用 "value" (Value)
        Use newUse = new Use(this, value);
        this.operands.add(newUse);
    }

    public void setOperand(int index, Value value) {
        if (index < 0 || index >= operands.size()) {
            throw new IndexOutOfBoundsException("Operand index out of bounds");
        }
        // Use 对象会自动处理旧 Value 的解绑和新 Value 的绑定
        this.operands.get(index).setValue(value);
    }
}
