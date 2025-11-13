package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.instruction.Instruction;
import io.github.tomorrow615.compiler.midend.llvm.type.VoidType; // 占位符
import io.github.tomorrow615.compiler.util.*;

import java.util.ArrayList;
import java.util.List;

public class BasicBlock extends Value {

    private final List<Instruction> instructions;
    private final Function parentFunction;

    public BasicBlock(String name, Function parentFunction) {
        // 基本块在 LLVM 中是 'label' 类型
        // 但在我们的模型中，作为 Value，它没有一个明确的类型，
        // 我们可以用 VoidType 占位，或创建一个 LabelType
        super(VoidType.get(), name); // 使用 VoidType 作为占位符
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

    @Override
    public String toString(SlotTracker tracker) {
        StringBuilder sb = new StringBuilder();

        // <-- 修改点: 使用 tracker
        sb.append(tracker.getName(this)).append(":\n");

        for (Instruction inst : instructions) {
            // <-- 修改点: 传递 tracker
            sb.append("  ").append(inst.toString(tracker)).append("\n");
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "BasicBlock<" + this.name + ">@" + hashCode(); // 调试用
    }
}