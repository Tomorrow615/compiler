package io.github.tomorrow615.compiler.backend.regalloc;

import io.github.tomorrow615.compiler.backend.mips.assembly.MipsInstruction;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsMove;
import io.github.tomorrow615.compiler.backend.mips.operand.Operand;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsBasicBlock;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;

import java.util.*;

public class InterferenceGraph {
    private final Map<Operand, Set<Operand>> adjList;
    private final Map<Operand, Integer> degrees;
    private final Map<Operand, Set<MipsInstruction>> moveList; // Node -> Related Move Instructions
    private final Set<Operand> precolored; // Physical registers

    public InterferenceGraph() {
        this.adjList = new HashMap<>();
        this.degrees = new HashMap<>();
        this.moveList = new HashMap<>();
        this.precolored = new HashSet<>();
    }

    public void build(MipsFunction func) {
        adjList.clear();
        degrees.clear();
        moveList.clear();
        precolored.clear();

        // [IRC] 1. Collect all precolored nodes (Physical Registers)
        // Just iterating over MipsRegister values isn't enough as we need Operand objects
        // We will collect them as we encounter them in instructions, plus explicitly add known ones if needed
        
        for (MipsBasicBlock block : func.getBlocks()) {
            Set<Operand> live = new HashSet<>(block.getLiveOut());

            List<MipsInstruction> insts = block.getInstructions();
            for (int i = insts.size() - 1; i >= 0; i--) {
                MipsInstruction inst = insts.get(i);
                
                // [IRC] Handle Precolored
                for (Operand def : inst.getDef()) {
                    if (def.isPhysical()) precolored.add(def);
                }
                for (Operand use : inst.getUse()) {
                    if (use.isPhysical()) precolored.add(use);
                }

                // [IRC] Handle Move Instructions for Coalescing
                if (inst instanceof MipsMove move) {
                    for (Operand use : inst.getUse()) moveList.computeIfAbsent(use, k -> new HashSet<>()).add(move);
                    for (Operand def : inst.getDef()) moveList.computeIfAbsent(def, k -> new HashSet<>()).add(move);
                }

                // 1. 处理 Def 造成的冲突
                for (Operand def : inst.getDef()) {
                    boolean isMove = (inst instanceof MipsMove);
                    
                    for (Operand l : live) {
                        // 如果是 move d, s 指令，且 l == s，则不添加边 (为了后续 Coalescing)
                        if (isMove && inst.getUse().contains(l)) {
                            continue;
                        }
                        // 自环也不添加
                        if (def.equals(l)) {
                            continue;
                        }
                        
                        // Move 指令的 Def 和 Use 之间也不添加边 (IRC 特性)
                        // This is partially covered by `if (isMove && inst.getUse().contains(l))`
                        // but specifically for the exact move processing.

                        addEdge(def, l);
                    }
                }

                // 2. 更新活跃集合
                for (Operand def : inst.getDef()) {
                    live.remove(def);
                }
                for (Operand use : inst.getUse()) {
                    live.add(use);
                }
            }
        }
    }

    public void addEdge(Operand u, Operand v) {
        if (u.equals(v)) return;
        
        Set<Operand> uAdj = adjList.computeIfAbsent(u, k -> new HashSet<>());
        if (uAdj.contains(v)) return; // Edge already exists

        Set<Operand> vAdj = adjList.computeIfAbsent(v, k -> new HashSet<>());
        
        uAdj.add(v);
        vAdj.add(u);

        // Update degrees (only for non-precolored nodes)
        if (!precolored.contains(u)) {
            degrees.put(u, degrees.getOrDefault(u, 0) + 1);
        } else {
             degrees.put(u, Integer.MAX_VALUE); // Precolored nodes satisfy ANY degree
        }
        
        if (!precolored.contains(v)) {
            degrees.put(v, degrees.getOrDefault(v, 0) + 1);
        } else {
             degrees.put(v, Integer.MAX_VALUE);
        }
    }

    public Set<Operand> getAdj(Operand u) {
        return adjList.getOrDefault(u, Collections.emptySet());
    }

    public int getDegree(Operand u) {
        return degrees.getOrDefault(u, 0);
    }

    public void decrementDegree(Operand u) {
        int d = degrees.getOrDefault(u, 0);
        degrees.put(u, d - 1);
    }
    
    public Set<MipsInstruction> getMoveList(Operand u) {
        return moveList.getOrDefault(u, Collections.emptySet());
    }
    
    public void mergeMoves(Operand u, Set<MipsInstruction> moves) {
        moveList.computeIfAbsent(u, k -> new HashSet<>()).addAll(moves);
    }
    
    public boolean isPrecolored(Operand u) {
        return precolored.contains(u);
    }
}
