package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.User;
import io.github.tomorrow615.compiler.util.*;

public abstract class Instruction extends User {
    private BasicBlock parentBlock;

    public Instruction(Type type, String name, BasicBlock parentBlock) {
        super(type, name);
        this.parentBlock = parentBlock;
        if (parentBlock != null) {
            parentBlock.addInstruction(this);
        }
    }

    protected Instruction(Type type, String name) {
        super(type, name);
        this.parentBlock = null;
    }

    public Instruction(Type type, BasicBlock parentBlock) {
        super(type);
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

    /**
     * 将指令从基本块中移除，并断开操作数连接
     */
    public void remove() {
        if (parentBlock != null) {
            parentBlock.removeInstruction(this);
        }
        removeUseFromOperands();
    }

    @Override
    public abstract String toString(SlotTracker tracker);

    @Override
    public String toString() {
        return "Instruction<" + type + ", name=" + name + ">@" + hashCode();
    }
}
