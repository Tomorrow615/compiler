package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.VoidType;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;

public class StoreInst extends Instruction {

    /**
     * 构造 'store <type> <value>, <type>* <pointer>'
     * @param value 要存储的值
     * @param pointer 目标内存地址
     * @param parentBlock 插入到的基本块
     */
    public StoreInst(Value value, Value pointer, BasicBlock parentBlock) {
        // store 指令没有返回值 (void) 且没有名字
        super(VoidType.get(), parentBlock);

        // 操作数0: value
        this.addOperand(value);
        // 操作数1: pointer
        this.addOperand(pointer);
    }

    public Value getValue() {
        return this.getOperand(0);
    }

    public Value getPointer() {
        return this.getOperand(1);
    }

    @Override
    public String toString() {
        Value val = getValue();
        Value ptr = getPointer();

        // e.g., store i32 %1, i32* %a
        return "store " + val.getType().toString() + " " + val.getName() + ", "
                + ptr.getType().toString() + " " + ptr.getName();
    }
}
