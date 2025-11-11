package io.github.tomorrow615.compiler.midend.llvm.value;

public class Use {
    private final User user;   // 谁在 "使用"
    private Value value; // 被 "使用" 的是谁

    public Use(User user, Value value) {
        this.user = user;
        this.value = value;
        // 建立双向连接
        if (value != null) {
            value.addUse(this);
        }
    }

    public User getUser() {
        return user;
    }

    public Value getValue() {
        return value;
    }

    public void setValue(Value value) {
        // 断开旧连接
        if (this.value != null) {
            this.value.removeUse(this);
        }
        // 建立新连接
        this.value = value;
        if (this.value != null) {
            this.value.addUse(this);
        }
    }
}
