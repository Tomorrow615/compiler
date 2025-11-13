package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.type.VoidType;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.*;

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
    public String toString(SlotTracker tracker) {
        if (isVoidRet()) {
            return "ret void";
        } else {
            Value retVal = getReturnValue();
            // <-- 修改点: 使用 tracker
            return "ret " + retVal.getType().toString() + " " + tracker.getName(retVal);
        }
    }

    @Override
    public String toString() {
        return "ReturnInst<" + (isVoidRet() ? "void" : "value") + ">@" + hashCode(); // 调试用
    }
}
