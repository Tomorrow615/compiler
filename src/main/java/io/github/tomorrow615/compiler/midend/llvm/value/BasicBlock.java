package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.*;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.util.*;

import java.util.ArrayList;
import java.util.List;

public class BasicBlock extends Value {
    private final List<Instruction> instructions;
    private final Function parentFunction;

    public BasicBlock(String name, Function parentFunction) {
        super(LabelType.get(), name);
        this.parentFunction = parentFunction;
        this.instructions = new ArrayList<>();
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

    public void addInstruction(Instruction inst) {
        this.instructions.add(inst);
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