package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.type.VoidType;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;

public class ReturnInst extends Instruction {

    /**
     * 构造 'ret void'
     * @param parentBlock 插入到的基本块
     */
    public ReturnInst(BasicBlock parentBlock) {
        super(VoidType.get(), parentBlock); // ret 指令本身没有类型和名字
    }

    /**
     * 构造 'ret <type> <value>'
     * @param value 要返回的值 (e.g., %1, 0)
     * @param parentBlock 插入到的基本块
     */
    public ReturnInst(Value value, BasicBlock parentBlock) {
        super(VoidType.get(), parentBlock);
        this.addOperand(value);
    }

    public boolean isVoidRet() {
        return this.operands.isEmpty();
    }

    public Value getReturnValue() {
        if (isVoidRet()) {
            return null;
        }
        return this.getOperand(0);
    }

    @Override
    public String toString() {
        if (isVoidRet()) {
            return "ret void";
        } else {
            Value retVal = getReturnValue();
            // e.g., ret i32 %1
            // e.g., ret i32 0 (ConstantInt)
            return "ret " + retVal.getType().toString() + " " + retVal.getName();
        }
    }
}
