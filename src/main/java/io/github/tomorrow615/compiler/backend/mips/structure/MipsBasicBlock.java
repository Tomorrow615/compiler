package io.github.tomorrow615.compiler.backend.mips.structure;

import io.github.tomorrow615.compiler.backend.mips.assembly.MipsInstruction;
import io.github.tomorrow615.compiler.backend.mips.operand.Operand;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MipsBasicBlock {
    private final String label;
    private final List<MipsInstruction> instructions;
    
    // CFG 
    private final List<MipsBasicBlock> predecessors;
    private final List<MipsBasicBlock> successors;
    
    // Liveness Analysis
    private final Set<Operand> use;   // Variables used in this block before being defined
    private final Set<Operand> def;   // Variables defined in this block
    private final Set<Operand> liveIn;  // Variables live at block entry
    private final Set<Operand> liveOut; // Variables live at block exit

    public MipsBasicBlock(String label) {
        this.label = label;
        this.instructions = new ArrayList<>();
        this.predecessors = new ArrayList<>();
        this.successors = new ArrayList<>();
        
        this.use = new HashSet<>();
        this.def = new HashSet<>();
        this.liveIn = new HashSet<>();
        this.liveOut = new HashSet<>();
    }

    public void addInstruction(MipsInstruction inst) {
        this.instructions.add(inst);
    }
    
    public void addPredecessor(MipsBasicBlock pred) {
        if (!predecessors.contains(pred)) {
            predecessors.add(pred);
        }
    }
    
    public void addSuccessor(MipsBasicBlock succ) {
        if (!successors.contains(succ)) {
            successors.add(succ);
        }
    }

    public String getLabel() {
        return label;
    }

    public List<MipsInstruction> getInstructions() {
        return instructions;
    }
    
    public List<MipsBasicBlock> getPredecessors() {
        return predecessors;
    }
    
    public List<MipsBasicBlock> getSuccessors() {
        return successors;
    }
    
    public Set<Operand> getUse() { return use; }
    public Set<Operand> getDef() { return def; }
    public Set<Operand> getLiveIn() { return liveIn; }
    public Set<Operand> getLiveOut() { return liveOut; }

    @Override
    public String toString() {
        return toString(true);
    }

    public String toString(boolean needLabel) {
        StringBuilder sb = new StringBuilder();
        // 只有需要标签时才输出
        if (needLabel) {
            sb.append(label).append(":\n");
        }
        // 输出所有指令
        for (MipsInstruction inst : instructions) {
            sb.append("\t").append(inst.toString()).append("\n");
        }
        return sb.toString();
    }
}
