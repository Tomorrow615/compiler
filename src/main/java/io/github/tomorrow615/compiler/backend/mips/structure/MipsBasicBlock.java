package io.github.tomorrow615.compiler.backend.mips.structure;

import io.github.tomorrow615.compiler.backend.mips.assembly.MipsInstruction;
import java.util.ArrayList;
import java.util.List;

public class MipsBasicBlock {
    private final String label;
    private final List<MipsInstruction> instructions;

    public MipsBasicBlock(String label) {
        this.label = label;
        this.instructions = new ArrayList<>();
    }

    public void addInstruction(MipsInstruction inst) {
        this.instructions.add(inst);
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        // 输出标签，例如 "main_entry:"
        sb.append(label).append(":\n");
        // 输出所有指令
        for (MipsInstruction inst : instructions) {
            sb.append("\t").append(inst.toString()).append("\n");
        }
        return sb.toString();
    }
}