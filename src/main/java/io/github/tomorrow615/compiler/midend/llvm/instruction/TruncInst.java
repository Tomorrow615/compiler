package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.*;

public class TruncInst extends Instruction {

    private final Type targetType;

    /**
     * 构造 'trunc <ty> <value> to <ty2>'
     * @param value 要截断的值
     * @param targetType 目标类型
     * @param name 结果的名字
     * @param parentBlock 插入到的基本块
     */
    public TruncInst(Value value, Type targetType, String name, BasicBlock parentBlock) {
        super(targetType, name, parentBlock);
        this.targetType = targetType;
        this.addOperand(value);
    }

    public Value getValueToTrunc() {
        return this.getOperand(0);
    }

    @Override
    public String toString(SlotTracker tracker) {
        Value val = getValueToTrunc();
        return tracker.getName(this) + " = trunc " + val.getType().toString() + " " + tracker.getName(val)
                + " to " + this.targetType.toString();
    }

    @Override
    public String toString() {
        return "TruncInst<" + this.name + ">@" + hashCode();
    }
}
