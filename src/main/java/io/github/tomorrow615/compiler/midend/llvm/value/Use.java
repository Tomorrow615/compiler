package io.github.tomorrow615.compiler.midend.llvm.value;

public class Use {
    private final User user;
    private Value value;

    public Use(User user, Value value) {
        this.user = user;
        this.value = value;
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
        if (this.value != null) {
            this.value.removeUse(this);
        }
        this.value = value;
        if (this.value != null) {
            this.value.addUse(this);
        }
    }
}
