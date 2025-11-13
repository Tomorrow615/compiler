package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.util.*;

public class AllocaInst extends Instruction {

    private final Type allocatedType; // 要分配的类型 (e.g., i32, [10 x i32])

    /**
     * 构造 'alloca <type>'
     * @param type 要分配的类型
     * @param name 结果指针的名字 (e.g., %a)
     * @param parentBlock 插入到的基本块
     */
    public AllocaInst(Type type, String name, BasicBlock parentBlock) {
        // alloca 指令的结果是一个指向 'type' 的指针
        super(new PointerType(type), name, parentBlock);
        this.allocatedType = type;
    }

    public Type getAllocatedType() {
        return allocatedType;
    }

    @Override
    public String toString(SlotTracker tracker) {
        return tracker.getName(this) + " = alloca " + this.allocatedType.toString();
    }

    @Override
    public String toString() {
        return "AllocaInst<" + this.name + ">@" + hashCode();
    }
}
