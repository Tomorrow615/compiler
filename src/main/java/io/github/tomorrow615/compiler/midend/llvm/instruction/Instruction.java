package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.User;
import io.github.tomorrow615.compiler.util.*;

public abstract class Instruction extends User {

    private BasicBlock parentBlock; // 该指令所属的基本块

    /**
     * 构造一条指令。
     * @param type 指令的结果类型 (e.g., i32 for add, void for store)
     * @param name 指令结果的名字 (e.g., %1, %addtmp)
     * @param parentBlock 这条指令应被插入到的基本块 (可选, 但推荐)
     */
    public Instruction(Type type, String name, BasicBlock parentBlock) {
        super(type, name);
        this.parentBlock = parentBlock;
        if (parentBlock != null) {
            parentBlock.addInstruction(this);
        }
    }

    /**
     * 构造一条没有名字的指令 (例如 store, ret)
     */
    public Instruction(Type type, BasicBlock parentBlock) {
        super(type); // 名字默认为 ""
        this.parentBlock = parentBlock;
        if (parentBlock != null) {
            parentBlock.addInstruction(this);
        }
    }

    public BasicBlock getParentBlock() {
        return parentBlock;
    }

    public void setParentBlock(BasicBlock parentBlock) {
        this.parentBlock = parentBlock;
    }

    @Override
    public abstract String toString(SlotTracker tracker);

    @Override
    public String toString() {
        // 这个方法现在只用于调试
        return "Instruction<" + type + ", name=" + name + ">@" + hashCode();
    }
}
