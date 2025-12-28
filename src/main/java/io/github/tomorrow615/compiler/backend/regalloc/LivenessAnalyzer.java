package io.github.tomorrow615.compiler.backend.regalloc;

import io.github.tomorrow615.compiler.backend.mips.MipsModule;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsInstruction;
import io.github.tomorrow615.compiler.backend.mips.operand.Operand;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsBasicBlock;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;

import java.util.*;

/**
 * 活跃变量分析
 * 计算每个基本块的 Use/Def 集合，以及 LiveIn/LiveOut 集合
 */
public class LivenessAnalyzer {
    private final MipsModule module;

    public LivenessAnalyzer(MipsModule module) {
        this.module = module;
    }

    public void analyze() {
        for (MipsFunction func : module.getFunctions()) {
            analyzeFunction(func);
        }
    }

    /**
     * BitSet-optimized Liveness Analysis
     */
    public void analyzeFunction(MipsFunction func) {
        // 1. Map all Operands to IDs [0, N)
        List<Operand> allOperands = collectAllOperands(func);
        int operandCount = allOperands.size();
        Map<Operand, Integer> operandToId = new HashMap<>(operandCount);
        for (int i = 0; i < operandCount; i++) {
            operandToId.put(allOperands.get(i), i);
        }

        List<MipsBasicBlock> blocks = func.getBlocks();
        int blockCount = blocks.size();

        // 2. Pre-compute Use/Def BitSets for each block
        java.util.BitSet[] useSets = new java.util.BitSet[blockCount];
        java.util.BitSet[] defSets = new java.util.BitSet[blockCount];
        java.util.BitSet[] liveInSets = new java.util.BitSet[blockCount];
        java.util.BitSet[] liveOutSets = new java.util.BitSet[blockCount];

        for (int i = 0; i < blockCount; i++) {
            useSets[i] = new java.util.BitSet(operandCount);
            defSets[i] = new java.util.BitSet(operandCount);
            liveInSets[i] = new java.util.BitSet(operandCount);
            liveOutSets[i] = new java.util.BitSet(operandCount);

            computeBlockUseDefBitSet(blocks.get(i), useSets[i], defSets[i], operandToId);
        }


        // [Optimization] Pre-compute block indices and successor indices
        Map<MipsBasicBlock, Integer> blockToId = new HashMap<>(blockCount);
        for (int i = 0; i < blockCount; i++) {
            blockToId.put(blocks.get(i), i);
        }

        int[][] successors = new int[blockCount][];
        for (int i = 0; i < blockCount; i++) {
            List<MipsBasicBlock> succs = blocks.get(i).getSuccessors();
            successors[i] = new int[succs.size()];
            for (int j = 0; j < succs.size(); j++) {
                successors[i][j] = blockToId.get(succs.get(j));
            }
        }

        // 3. Iterative Dataflow (Backward)
        boolean changed = true;
        while (changed) {
            changed = false;
            // Iterate blocks in reverse order for faster convergence
            for (int i = blockCount - 1; i >= 0; i--) {
                java.util.BitSet newLiveOut = new java.util.BitSet(operandCount);
                // MipsBasicBlock block = blocks.get(i); // block unused in optimized loop

                // Union successors' LiveIn using pre-computed indices
                for (int succIdx : successors[i]) {
                    newLiveOut.or(liveInSets[succIdx]);
                }

                // LiveIn = Use U (LiveOut - Def)
                java.util.BitSet newLiveIn = (java.util.BitSet) newLiveOut.clone();
                newLiveIn.andNot(defSets[i]);
                newLiveIn.or(useSets[i]);

                // Check for changes
                if (!newLiveIn.equals(liveInSets[i]) || !newLiveOut.equals(liveOutSets[i])) {
                    liveInSets[i] = newLiveIn;
                    liveOutSets[i] = newLiveOut;
                    changed = true;
                }
            }
        }

        // 4. Write back to MipsBasicBlock Sets (for compatibility)
        for (int i = 0; i < blockCount; i++) {
            MipsBasicBlock block = blocks.get(i);
            
            block.getLiveIn().clear();
            block.getLiveOut().clear();

            java.util.BitSet in = liveInSets[i];
            java.util.BitSet out = liveOutSets[i];

            for (int bit = in.nextSetBit(0); bit >= 0; bit = in.nextSetBit(bit + 1)) {
                block.getLiveIn().add(allOperands.get(bit));
            }
            for (int bit = out.nextSetBit(0); bit >= 0; bit = out.nextSetBit(bit + 1)) {
                block.getLiveOut().add(allOperands.get(bit));
            }
        }
    }

    private java.util.ArrayList<Operand> collectAllOperands(MipsFunction func) {
        java.util.ArrayList<Operand> list = new java.util.ArrayList<>();
        Set<Operand> seen = new HashSet<>();
        
        for (MipsBasicBlock block : func.getBlocks()) {
            for (MipsInstruction inst : block.getInstructions()) {
                for (Operand op : inst.getDef()) {
                    if (seen.add(op)) list.add(op);
                }
                for (Operand op : inst.getUse()) {
                    if (seen.add(op)) list.add(op);
                }
            }
        }
        return list;
    }

    private void computeBlockUseDefBitSet(MipsBasicBlock block, 
                                          java.util.BitSet use, 
                                          java.util.BitSet def,
                                          Map<Operand, Integer> opToId) {
        // Local calculation, strictly follow standard definition:
        // Use: Upward exposed uses
        // Def: Killed definitions
        
        // Clear logic handled by new BitSet()

        for (MipsInstruction inst : block.getInstructions()) {
            // Check Uses FIRST
            for (Operand op : inst.getUse()) {
                Integer id = opToId.get(op);
                if (id != null) {
                    // If not defined earlier in this block (not in def set yet)
                    if (!def.get(id)) {
                        use.set(id);
                    }
                }
            }

            // Then Check Defs
            for (Operand op : inst.getDef()) {
                Integer id = opToId.get(op);
                if (id != null) {
                    def.set(id);
                }
            }
        }
    }

    // Deprecated old methods removed for clarity
    // (computeUseDef, computeLiveInOut)
}
