package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.*;

public class LoadInst extends Instruction {

    /**
     * 构造 'load <type>, <type>* <pointer>'
     * @param pointer 要加载的内存地址 (必须是指针类型)
     * @param name 结果的名字 (e.g., %val)
     * @param parentBlock 插入到的基本块
     */
    public LoadInst(Value pointer, String name, BasicBlock parentBlock) {
        // load 的结果类型是 "指针指向的类型"
        super(((PointerType) pointer.getType()).getTargetType(), name, parentBlock);

        // 添加操作数
        this.addOperand(pointer);
    }

    public Value getPointer() {
        return this.getOperand(0);
    }

    @Override
    public String toString(SlotTracker tracker) {
        Value ptr = getPointer();
        return tracker.getName(this) + " = load " + this.getType().toString() + ", "
                + ptr.getType().toString() + " " + tracker.getName(ptr);
    }

    @Override
    public String toString() {
        return "LoadInst<" + this.name + ">@" + hashCode();
    }
}
