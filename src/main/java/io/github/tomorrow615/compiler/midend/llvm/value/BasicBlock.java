package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.*;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.util.*;

import java.util.ArrayList;
import java.util.List;

public class BasicBlock extends Value {
    private final List<Instruction> instructions;
    private Function parentFunction;  // 改为非 final，支持内联时修改
    
    // CFG 关系：前驱和后继基本块
    private final List<BasicBlock> predecessors;
    private final List<BasicBlock> successors;

    public BasicBlock(String name, Function parentFunction) {
        super(LabelType.get(), name);
        this.parentFunction = parentFunction;
        this.instructions = new ArrayList<>();
        this.predecessors = new ArrayList<>();
        this.successors = new ArrayList<>();
        if (parentFunction != null) {
            parentFunction.addBasicBlock(this);
        }
    }

    public List<Instruction> getInstructions() {
        return instructions;
    }

    public Function getParentFunction() {
        return parentFunction;
    }
    
    /**
     * 设置父函数（用于函数内联时克隆块）
     */
    public void setParentFunction(Function parentFunction) {
        this.parentFunction = parentFunction;
    }


    public void addInstruction(Instruction inst) {
        this.instructions.add(inst);
    }

    // === CFG 方法 ===
    public List<BasicBlock> getPredecessors() {
        return predecessors;
    }

    public List<BasicBlock> getSuccessors() {
        return successors;
    }

    public void addPredecessor(BasicBlock bb) {
        if (!predecessors.contains(bb)) {
            predecessors.add(bb);
        }
    }

    public void addSuccessor(BasicBlock bb) {
        if (!successors.contains(bb)) {
            successors.add(bb);
        }
    }

    public boolean hasTerminator() {
        if (this.instructions.isEmpty()) {
            return false;
        }
        Instruction lastInst = this.instructions.get(this.instructions.size() - 1);
        return (lastInst instanceof BranchInst || lastInst instanceof ReturnInst);
    }

    @Override
    public String toString(SlotTracker tracker) {
        StringBuilder sb = new StringBuilder();
        sb.append(tracker.getName(this)).append(":\n");
        for (Instruction inst : instructions) {
            sb.append("  ").append(inst.toString(tracker)).append("\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "BasicBlock<" + this.name + ">@" + hashCode(); // 调试用
    }
}