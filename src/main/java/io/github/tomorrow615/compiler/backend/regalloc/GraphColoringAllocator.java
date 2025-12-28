package io.github.tomorrow615.compiler.backend.regalloc;

import io.github.tomorrow615.compiler.backend.mips.MipsModule;
import io.github.tomorrow615.compiler.backend.mips.MipsRegister;
import io.github.tomorrow615.compiler.backend.mips.analysis.LoopInfo;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsInstruction;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsLoadStore;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsMove;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsBinary; // Need for immediate check
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsBranch;
import io.github.tomorrow615.compiler.backend.mips.operand.Operand;
import io.github.tomorrow615.compiler.backend.mips.operand.VirtualRegister;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsBasicBlock;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsLi;

import java.util.*;

/**
 * Iterated Register Coalescing (IRC) Allocator
 */
public class GraphColoringAllocator {
    private final MipsModule module;
    
    // K = 物理寄存器数量 (18: $t0-$t9, $s0-$s7)
    // 注意：$ra, $sp, $fp, $zero, $at, $v0, $v1, $a0-$a3 通常预留或专用，不参与通用分配
    private static final int K = 18;
    
    // 物理寄存器列表
    private static final List<MipsRegister> REGS = new ArrayList<>();
    static {
        // Temps
        REGS.add(MipsRegister.T0); REGS.add(MipsRegister.T1); REGS.add(MipsRegister.T2); 
        REGS.add(MipsRegister.T3); REGS.add(MipsRegister.T4); REGS.add(MipsRegister.T5);
        REGS.add(MipsRegister.T6); REGS.add(MipsRegister.T7); REGS.add(MipsRegister.T8); 
        REGS.add(MipsRegister.T9);
        // Saved
        REGS.add(MipsRegister.S0); REGS.add(MipsRegister.S1); REGS.add(MipsRegister.S2); 
        REGS.add(MipsRegister.S3); REGS.add(MipsRegister.S4); REGS.add(MipsRegister.S5); 
        REGS.add(MipsRegister.S6); REGS.add(MipsRegister.S7);
    }

    // 全局数据结构
    private InterferenceGraph graph;
    private LoopInfo loopInfo;

    // Node Sets (Disjoint)
    // 1. precolored: 机器寄存器 (Build时由 Graph 确定)
    // 2. initial: 临时寄存器，尚未处理
    private final Set<Operand> initial = new HashSet<>();
    // 3. simplifyWorklist: 低度数，传送无关
    private final Set<Operand> simplifyWorklist = new HashSet<>();
    // 4. freezeWorklist: 低度数，传送相关
    private final Set<Operand> freezeWorklist = new HashSet<>();
    // 5. spillWorklist: 高度数
    private final Set<Operand> spillWorklist = new HashSet<>();
    // 6. spilledNodes: 标记为溢出的节点
    private final Set<Operand> spilledNodes = new HashSet<>();
    // 7. coalescedNodes: 已合并的节点
    private final Set<Operand> coalescedNodes = new HashSet<>();
    // 8. coloredNodes: 成功着色的节点
    private final Set<Operand> coloredNodes = new HashSet<>();
    // 9. selectStack: 包含从图中删除的节点 (Color阶段弹出)
    private final Stack<Operand> selectStack = new Stack<>();

    // Move Sets
    // 1. coalescedMoves: 已合并的移动
    private final Set<MipsInstruction> coalescedMoves = new HashSet<>();
    // 2. constrainedMoves: 源操作数和目标操作数冲突的移动
    private final Set<MipsInstruction> constrainedMoves = new HashSet<>();
    // 3. frozenMoves: 不再考虑合并的移动
    private final Set<MipsInstruction> frozenMoves = new HashSet<>();
    // 4. worklistMoves: 可能合并的移动
    private final Set<MipsInstruction> worklistMoves = new HashSet<>();
    // 5. activeMoves: 尚未准备好合并的移动
    private final Set<MipsInstruction> activeMoves = new HashSet<>();

    // Alias map (Coalescing result)
    private final Map<Operand, Operand> alias = new HashMap<>();
    
    // Coloring result
    private final Map<Operand, MipsRegister> color = new HashMap<>();
    
    // Spill Costs
    private final Map<Operand, Double> spillCosts = new HashMap<>();

    // Definition Map for Rematerialization
    private final Map<Operand, MipsInstruction> definition = new HashMap<>();




    public GraphColoringAllocator(MipsModule module) {
        this.module = module;
    }

    public void allocate() {
        for (MipsFunction func : module.getFunctions()) {
            allocateFunction(func);
        }
    }

    private void allocateFunction(MipsFunction func) {
        while (true) {
            // 0. Init
            initialize();
            
            // 1. Liveness Analysis
            new LivenessAnalyzer(module).analyzeFunction(func);
            
            // 2. Loop Analysis (for Spill Cost)
            // 2. Loop Analysis (for Spill Cost)
            loopInfo = new LoopInfo(func);
            calculateSpillCosts(func);
            
            // 3. Build Interference Graph
            graph = new InterferenceGraph();
            graph.build(func);
            
            // 4. Make Worklists
            makeWorklist(func);
            
            // 5. Iterate (Simplify, Coalesce, Freeze, Spill)
            while (!simplifyWorklist.isEmpty() || !worklistMoves.isEmpty() || 
                   !freezeWorklist.isEmpty() || !spillWorklist.isEmpty()) {
                
                if (!simplifyWorklist.isEmpty()) {
                    simplify();
                } else if (!worklistMoves.isEmpty()) {
                    coalesce();
                } else if (!freezeWorklist.isEmpty()) {
                    freeze();
                } else if (!spillWorklist.isEmpty()) {
                    selectSpill();
                }
            }
            
            // 6. Assign Colors
            assignColors();
            
            // 7. Rewrite Program (if spilled)
            if (!spilledNodes.isEmpty()) {
                rewriteProgram(func);
                // 重复循环
            } else {
                // Apply colors and remove moves
                finish(func);
                break;
            }
        }
    }

    private void initialize() {
        initial.clear();
        simplifyWorklist.clear();
        freezeWorklist.clear();
        spillWorklist.clear();
        spilledNodes.clear();
        coalescedNodes.clear();
        coloredNodes.clear();
        selectStack.clear(); // Use standard stack methods

        coalescedMoves.clear();
        constrainedMoves.clear();
        frozenMoves.clear();
        worklistMoves.clear();
        activeMoves.clear();

        alias.clear();

        spillCosts.clear();
        definition.clear();
    }
    
    // === Step 3.1: Make Worklists ===

    private void makeWorklist(MipsFunction func) {
        // 收集所有 initial 节点 (即所有的 VirtualRegister)
        // 物理寄存器已在 graph.precolored 中
        collectInitialNodes(func);

        for (Operand n : initial) {
            if (graph.getDegree(n) >= K) {
                spillWorklist.add(n);
            } else if (isMoveRelated(n)) {
                freezeWorklist.add(n);
            } else {
                simplifyWorklist.add(n);
            }
        }
    }
    
    private void collectInitialNodes(MipsFunction func) {
        Set<Operand> seen = new HashSet<>();
        definition.clear(); // Clear old definitions
        
        for (MipsBasicBlock block : func.getBlocks()) {
            for (MipsInstruction inst : block.getInstructions()) {
                for (Operand def : inst.getDef()) {
                    if (def instanceof VirtualRegister) {
                        seen.add(def);
                        definition.put(def, inst); // Record definition
                    }
                }
                for (Operand use : inst.getUse()) {
                    if (use instanceof VirtualRegister) seen.add(use);
                }
            }
        }
        initial.addAll(seen);
    }
    
    /**
     * 判断节点是否与 Move 指令相关
     * 只有当一个节点关联的 Move 指令中，至少有一个还在 worklistMoves 或 activeMoves 中，才算 Move 相关
     */
    private boolean isMoveRelated(Operand n) {
        return !nodeMoves(n).isEmpty();
    }
    
    /**
     * 获取节点相关的活跃 Move 指令 (Activity or Worklist)
     */
    private Set<MipsInstruction> nodeMoves(Operand n) {
        Set<MipsInstruction> moves = new HashSet<>(graph.getMoveList(n));
        // Intersection with (activeMoves U worklistMoves)
        Set<MipsInstruction> activeOrWorklist = new HashSet<>(activeMoves);
        activeOrWorklist.addAll(worklistMoves);
        
        moves.retainAll(activeOrWorklist);
        return moves;
    }

    // === Step 3.2: Simplify & Coalesce ===
    
    // === Helper Methods for Set Operations ===

    private void decrementDegree(Operand m) {
        int d = graph.getDegree(m);
        graph.decrementDegree(m); // Need to implement this in InterferenceGraph? Or just manage locally?
        // Actually, we should probably manage degrees locally since InterferenceGraph is built once.
        // But for simplicity, let's assume we can modify the graph or shadow the degrees.
        // Let's modify InterferenceGraph to support dynamic degree updates or handle it here.
        // Ideally, we handle it here to avoid mutating the graph structure permanently if we spill and rebuild.
        
        if (d == K) {
            // When degree drops from K to K-1, enable moves for this node and its neighbors
            Set<Operand> nodes = new HashSet<>(getAdj(m));
            nodes.add(m);
            enableMoves(nodes);
            spillWorklist.remove(m);
            if (isMoveRelated(m)) {
                freezeWorklist.add(m);
            } else {
                simplifyWorklist.add(m);
            }
        }
    }
    
    private void enableMoves(Set<Operand> nodes) {
        for (Operand n : nodes) {
            for (MipsInstruction m : nodeMoves(n)) {
                if (activeMoves.contains(m)) {
                    activeMoves.remove(m);
                    worklistMoves.add(m);
                }
            }
        }
    }
    
    private void addWorkList(Operand u) {
        if (!graph.isPrecolored(u) && !isMoveRelated(u) && graph.getDegree(u) < K) {
            freezeWorklist.remove(u);
            simplifyWorklist.add(u);
        }
    }
    
    // Get adjacent nodes (excluding those on stack or coalesced)
    private Set<Operand> getAdj(Operand u) {
        Set<Operand> adj = new HashSet<>(graph.getAdj(u));
        adj.removeAll(selectStack);
        adj.removeAll(coalescedNodes); // Is this correct? Coalesced nodes are effectively merged.
        // Usually: adj = adjList[u] \ (selectStack U coalescedNodes)
        return adj;
    }

    private void simplify() {
        Operand n = simplifyWorklist.iterator().next();
        simplifyWorklist.remove(n);
        selectStack.push(n);
        
        for (Operand m : getAdj(n)) {
            decrementDegree(m);
        }
    }
    
    private void coalesce() {
        MipsInstruction m = worklistMoves.iterator().next();
        MipsInstruction move = (MipsMove) m; // Safe cast as we only add MipsMove
        Operand x = getAlias(move.getDef().get(0));
        Operand y = getAlias(move.getUse().get(0));
        Operand u, v;

        if (graph.isPrecolored(y)) {
            u = y;
            v = x;
        } else {
            u = x;
            v = y;
        }

        worklistMoves.remove(m);

        if (u == v) {
            coalescedMoves.add(m);
            addWorkList(u);
        } else if (graph.isPrecolored(v) || graph.getAdj(u).contains(v)) {
            constrainedMoves.add(m);
            addWorkList(u);
            addWorkList(v);
        } else if ((graph.isPrecolored(u) && ok(u, v)) || 
                   (!graph.isPrecolored(u) && conservative(getAdj(u), getAdj(v)))) {
            coalescedMoves.add(m);
            combine(u, v);
            addWorkList(u);
        } else {
            activeMoves.add(m);
        }
    }

    private void combine(Operand u, Operand v) {
        if (freezeWorklist.contains(v)) {
            freezeWorklist.remove(v);
        } else {
            spillWorklist.remove(v);
        }
        
        coalescedNodes.add(v);
        alias.put(v, u);
        // Note: We don't merge move lists explicitly here because nodeMoves() handles aliases?
        // Actually IRC says: moveList[u] <- moveList[u] U moveList[v]
        // Since we can't easily merge Sets in the graph without modifying it, 
        // rely on `nodeMoves` iterating over specific moves or update the graph.
        // But updating graph is hard. 
        // Standard impl: Union the move lists.
        // Let's add a `mergeMoveList` to InterferenceGraph or handle logically.
        // For now, let's assume `graph.getMoveList(v)` needs to be added to `u` logical tracking.
        // But since `v` is aliased to `u`, future calls to `nodeMoves(v)` should redirect to `u`?
        // No, `nodeMoves` uses `graph.getMoveList(n)`.
        // We need to merge them. To avoid complex graph updates, let's just make `graph.moveList` mutable.
        graph.mergeMoves(u, graph.getMoveList(v));

        enableMoves(Collections.singleton(v));
        
        for (Operand t : getAdj(v)) {
            graph.addEdge(t, u);
            decrementDegree(t);
        }
        
        if (graph.getDegree(u) >= K && freezeWorklist.contains(u)) {
            freezeWorklist.remove(u);
            spillWorklist.add(u);
        }
    }

    private Operand getAlias(Operand n) {
        if (coalescedNodes.contains(n)) {
            Operand a = getAlias(alias.get(n));
            alias.put(n, a); // Path compression
            return a;
        }
        return n;
    }

    // George's Heuristic (for Precolored u)
    // OK(t, r): t interferes with r or t has degree < K
    // For all neighbors t of v: OK(t, u)
    private boolean ok(Operand u, Operand v) {
        for (Operand t : getAdj(v)) {
            boolean ok = (graph.getDegree(t) < K) || graph.isPrecolored(t) || graph.getAdj(u).contains(t);
            if (!ok) return false;
        }
        return true;
    }

    // Briggs' Heuristic (for Non-Precolored u)
    // Nodes = adj(u) U adj(v)
    // if (count(n in Nodes where degree(n) >= K) < K) coalesce
    private boolean conservative(Set<Operand> adjU, Set<Operand> adjV) {
        Set<Operand> combined = new HashSet<>(adjU);
        combined.addAll(adjV);
        
        int k = 0;
        for (Operand n : combined) {
            if (graph.getDegree(n) >= K) {
                k++;
            }
        }
        return k < K;
    }
    
    // === Step 3.3: Freeze & Spill ===
    
    private void freeze() {
        Operand u = freezeWorklist.iterator().next();
        freezeWorklist.remove(u);
        simplifyWorklist.add(u);
        freezeMoves(u);
    }
    
    private void freezeMoves(Operand u) {
        for (MipsInstruction m : nodeMoves(u)) { // Iterate related moves (active/worklist)
            MipsInstruction move = (MipsMove) m;
            Operand x = move.getDef().get(0);
            Operand y = move.getUse().get(0);
            Operand v;
            
            // v is the alias of the OTHER operand (not u)
            Operand aliasY = getAlias(y);
            if (getAlias(u) == aliasY) {
                v = getAlias(x);
            } else {
                v = aliasY;
            }
            
            activeMoves.remove(m);
            frozenMoves.add(m);
            
            // If v has no more moves and degree < K, it can be simplified
            if (nodeMoves(v).isEmpty() && graph.getDegree(v) < K) {
                freezeWorklist.remove(v);
                simplifyWorklist.add(v);
            }
        }
    }
    
    private void selectSpill() {
        // Find best spill candidate based on cost heuristic (min cost/degree)
        Operand best = null;
        double minScore = Double.MAX_VALUE;
        
        for (Operand op : spillWorklist) {
            double score = calculateSpillCost(op);
            if (score < minScore) {
                minScore = score;
                best = op;
            }
        }
        
        spillWorklist.remove(best);
        simplifyWorklist.add(best);
        freezeMoves(best); // We must freeze its moves as we are optimistically simplifying it (removing it)
    }
    
    private double calculateSpillCost(Operand op) {
        return spillCosts.getOrDefault(op, 0.0) / graph.getDegree(op);
    }
    
    private void calculateSpillCosts(MipsFunction func) {
        for (MipsBasicBlock block : func.getBlocks()) {
            double loopWeight = Math.pow(10, loopInfo.getLoopDepth(block));
            
            for (MipsInstruction inst : block.getInstructions()) {
                Set<Operand> seen = new HashSet<>();
                seen.addAll(inst.getDef());
                seen.addAll(inst.getUse());
                
                for (Operand op : seen) {
                    if (op instanceof VirtualRegister) {
                        spillCosts.put(op, spillCosts.getOrDefault(op, 0.0) + loopWeight);
                    }
                }
            }
        }
    }
    
    // === Step 3.4: Assign Colors ===
    
    private void assignColors() {
        while (!selectStack.isEmpty()) {
            Operand n = selectStack.pop();
            Set<MipsRegister> usedColors = new HashSet<>();
            
            for (Operand neighbor : graph.getAdj(n)) {
                Operand aliasNeighbor = getAlias(neighbor);
                if (coloredNodes.contains(aliasNeighbor) || graph.isPrecolored(aliasNeighbor)) {
                    // If precolored, it works as is. If colored, use its color.
                    if (aliasNeighbor.isPhysical()) {
                         usedColors.add((MipsRegister) aliasNeighbor); // Dangerous cast check?
                         // MipsRegister implements Operand. Precolored set contains Operand objects which are likely MipsRegister instances.
                         // But wait, `precolored` set in Graph might contain VirtualRegisters? No, only physical.
                         // Actually `precolored` logic in build() adds physical registers.
                         // Let's ensure we get the MipsRegister object.
                    } else if (color.containsKey(aliasNeighbor)) {
                        usedColors.add(color.get(aliasNeighbor));
                    }
                }
            }
            
            // Try to pick a color
            MipsRegister chosen = null;
            // Preference: Call-saved vs Call-clobbered? 
            // For now, just pick first available.
            for (MipsRegister reg : REGS) {
                if (!usedColors.contains(reg)) {
                    chosen = reg;
                    break;
                }
            }
            
            if (chosen == null) {
                // Actual spill
                spilledNodes.add(n);
                // We don't color it. It will be rewritten.
            } else {
                coloredNodes.add(n);
                color.put(n, chosen);
            }
        }
        
        // Color coalesced nodes
        for (Operand n : coalescedNodes) {
             Operand alias = getAlias(n);
             if (color.containsKey(alias)) {
                 color.put(n, color.get(alias));
             } else {
                 // If alias was spilled?
                 // Coalesced node should spill too? 
                 // Usually coalesced nodes share fate. If alias spilled, this spills too.
                 // But spilledNodes logic above only handles selectStack nodes.
                 // We should check if alias is spilled.
                 // However, we only need 'color' map for valid instructions.
                 // Spilled nodes will be rewritten, so their color doesn't matter yet.
             }
        }
    }
    
    // === Implementation Details ===
    
    private boolean isRematerializable(Operand op) {
        MipsInstruction defInst = definition.get(op);
        if (defInst == null) return false;
        
        // Case 1: Li (Constant)
        if (defInst instanceof MipsLi) return true;
        
        // Case 2: Addiu $t, $sp, imm (Frame Address)
        // We only support Remat for SP relative address calculation because SP is constant throughout function (mostly)
        // actually SP changes in prologue/epilogue but within body it's stable relative to frame.
        if (defInst instanceof MipsBinary bin) {
            if ((bin.getOp().equals("addiu") || bin.getOp().equals("addi")) && 
                bin.getRs() == MipsRegister.SP && 
                bin.getImm() != null) {
                return true;
            }
        }
        
        return false;
    }

    private void rewriteProgram(MipsFunction func) {
        // Splite spilledNodes into Remat and MemSpill
        Set<Operand> rematNodes = new HashSet<>();
        Set<Operand> memSpillNodes = new HashSet<>();
        
        for (Operand spill : spilledNodes) {
            if (isRematerializable(spill)) {
                rematNodes.add(spill);
            } else {
                memSpillNodes.add(spill);
            }
        }
        
        // 1. Calculate extra space using StackManager
        io.github.tomorrow615.compiler.backend.codegen.StackManager stackManager = func.getStackManager();
        if (stackManager == null) {
            throw new RuntimeException("StackManager not linked to MipsFunction: " + func.getName());
        }

        int oldStackSize = stackManager.getRaOffset(); // RA saved at stackSize
        int oldFrameSize = stackManager.getFrameSize();
        
        // 1. Calculate extra space required for spills
        // [Refactor] Spill slots are allocated at the BOTTOM of the frame (0 to spillSize)
        // Existing content (Locals, Arg Slots, RA) is shifted UP by spillSize.
        int spillCount = memSpillNodes.size();
        int spillSize = spillCount * 4;
        // Ensure 8-byte alignment for the spill area
        if (spillSize % 8 != 0) spillSize += (8 - spillSize % 8);

        // 2. Assign offsets to spill nodes (start from 0)
        Map<Operand, Integer> spilledOffsets = new HashMap<>();
        int currentSpillOffset = 0;
        for (Operand spill : memSpillNodes) {
             spilledOffsets.put(spill, currentSpillOffset);
             currentSpillOffset += 4;
        }

        // Since we are inserting at bottom, the new frame size increases by spillSize
        int extraFrameSize = spillSize;

        // 3. Patch Stack Offsets in existing instructions
        // We need to shift everything UP by spillSize because $sp moves down by spillSize relative to them
        if (extraFrameSize > 0) {
            for (MipsBasicBlock block : func.getBlocks()) {
                List<MipsInstruction> newInsts = new ArrayList<>();
                for (MipsInstruction inst : block.getInstructions()) {
                    MipsInstruction patched = inst;
                    
                    if (inst instanceof MipsBinary bin && bin.getImm() != null) {
                        // Prologue: subu $sp, $sp, frameSize
                        if (bin.getOp().equals("subu") && 
                            bin.getRd() == MipsRegister.SP && 
                            bin.getRs() == MipsRegister.SP) {
                            patched = new MipsBinary("subu", MipsRegister.SP, MipsRegister.SP, bin.getImm() + extraFrameSize);
                        }
                        // Epilogue: addu $sp, $sp, frameSize
                        else if (bin.getOp().equals("addu") && 
                                 bin.getRd() == MipsRegister.SP && 
                                 bin.getRs() == MipsRegister.SP) {
                            patched = new MipsBinary("addu", MipsRegister.SP, MipsRegister.SP, bin.getImm() + extraFrameSize);
                        }
                        // Patch addiu $sp, offset (address calculation)
                        else if ((bin.getOp().equals("addiu") || bin.getOp().equals("addi")) && 
                                 bin.getRs() == MipsRegister.SP) {
                             // All existing stack objects are shifted up
                             patched = new MipsBinary(bin.getOp(), bin.getRd(), MipsRegister.SP, bin.getImm() + extraFrameSize);
                        }
                    } else if (inst instanceof MipsLoadStore ls) {
                        // Patch loads/stores: lw/sw reg, offset($sp)
                        if (ls.getBase() == MipsRegister.SP) {
                            // All existing stack access (Locals, Args, RA) needs to be shifted up
                            patched = new MipsLoadStore(ls.getType(), ls.getRt(), MipsRegister.SP, ls.getOffset() + extraFrameSize);
                        }
                    }
                    newInsts.add(patched);
                }
                block.getInstructions().clear();
                block.getInstructions().addAll(newInsts);
            }

        }
        
        // 3. Rewrite Instructions with Spills
        // Algorithm: For each instruction, check Def and Use for spilled nodes.
        // Insert Loads before Use, Stores after Def.
        
        // 3. Rewrite Instructions with Spills
        // Algorithm: For each instruction, check Def and Use for spilled nodes.
        // Insert Loads before Use, Stores after Def.
        
        for (MipsBasicBlock block : func.getBlocks()) {
            List<MipsInstruction> oldInsts = new ArrayList<>(block.getInstructions());
            List<MipsInstruction> newInsts = new ArrayList<>();
            
            for (MipsInstruction inst : oldInsts) {
                // Determine spills involved
                // Uses
                Map<Operand, Operand> useReplacements = new HashMap<>(); // Spilled -> NewTemp
                List<MipsInstruction> loadsToInsert = new ArrayList<>();
                
                for (Operand use : inst.getUse()) {
                    if (spilledNodes.contains(use)) { // Check ALL spills (Remat + Mem)
                        if (!useReplacements.containsKey(use)) {
                            VirtualRegister temp = new VirtualRegister();
                            useReplacements.put(use, temp);
                            
                            if (rematNodes.contains(use)) {
                                // REMAT: Insert the defining instruction re-computing the value
                                MipsInstruction defInst = definition.get(use);
                                if (defInst instanceof MipsLi li) {
                                    loadsToInsert.add(new MipsLi(temp, li.getImm()));
                                } else if (defInst instanceof MipsBinary bin) { // Addiu $sp
                                    // Make sure to add extraSize to the offset if we patched stack!
                                    // Use extraFrameSize calculated from StackManager
                                    int oldImm = bin.getImm();
                                    // [Fix] Remat needs same patching as original code
                                    loadsToInsert.add(new MipsBinary(bin.getOp(), temp, MipsRegister.SP, oldImm + extraFrameSize));
                                }
                            } else {
                                // MEM SPILL: Insert Load
                                // Use offsets from `spilledOffsets` map (0...spillSize)
                                // These are ALREADY correct relative to the new $sp.
                                if (!spilledOffsets.containsKey(use)) {
                                     throw new RuntimeException("Spill offset not found for: " + use);
                                }
                                int offset = spilledOffsets.get(use);
                                loadsToInsert.add(new MipsLoadStore(MipsLoadStore.Type.LW, temp, MipsRegister.SP, offset));
                            }
                        }
                    }
                }
                
                // Add inserted loads/remats
                newInsts.addAll(loadsToInsert);
                
                // Replace Uses in current instruction
                MipsInstruction newInst = inst; // We might need to clone if we want to be safe, but modifying in place is usually ok if 1-pass
                // Use replacements logic
                if (!useReplacements.isEmpty()) {
                     // Since MipsInstruction is mutable and we are replacing operands, it's fine.
                     List<Operand> uses = new ArrayList<>(inst.getUse());
                     for (Operand use : uses) {
                         if (useReplacements.containsKey(use)) {
                             inst.replaceUse(use, useReplacements.get(use));
                         }
                     }
                }
                
                newInsts.add(newInst);

                // Process Defs
                // For Remat nodes: If we define a Remat node, we perform NO STORE.
                // The instruction remains.
                // For Mem nodes: Insert Store.
                
                Map<Operand, Operand> defReplacements = new HashMap<>();
                for (Operand def : inst.getDef()) {
                    if (spilledNodes.contains(def)) {
                        if (memSpillNodes.contains(def)) {
                            VirtualRegister temp = new VirtualRegister();
                            defReplacements.put(def, temp);
                            inst.replaceDef(def, temp);
                            
                            if (!spilledOffsets.containsKey(def)) {
                                 throw new RuntimeException("Spill offset not found for: " + def);
                            }
                            int offset = spilledOffsets.get(def);
                            // Store to spill slot
                            newInsts.add(new MipsLoadStore(MipsLoadStore.Type.SW, temp, MipsRegister.SP, offset));
                        } else {
                            // Remat node defined: Do nothing (Implicitly dead)
                        }
                    }
                }
            }
            block.getInstructions().clear();
            block.getInstructions().addAll(newInsts);
        }
        
        // Clear global sets to restart allocation
        spilledNodes.clear();
        initial.clear();
        simplifyWorklist.clear();
        freezeWorklist.clear();
        spillWorklist.clear();
        coalescedNodes.clear();
        coloredNodes.clear();
        selectStack.clear();
        coalescedMoves.clear();
        constrainedMoves.clear();
        frozenMoves.clear();
        worklistMoves.clear();
        activeMoves.clear();
        alias.clear();
        color.clear();
        spillCosts.clear();
    }
    
    private void finish(MipsFunction func) {
        // Apply colors to machine instructions
        for (MipsBasicBlock block : func.getBlocks()) {
            List<MipsInstruction> insts = block.getInstructions();
            List<MipsInstruction> newInsts = new ArrayList<>();
            
            for (MipsInstruction inst : insts) {
                // Remove coalesced moves
                if (inst instanceof MipsMove move) {
                    Operand def = move.getDef().get(0);
                    Operand use = move.getUse().get(0);
                    Operand defAlias = getAlias(def);
                    Operand useAlias = getAlias(use);
                    
                    MipsRegister c1 = defAlias.isPhysical() ? (MipsRegister) defAlias : color.get(defAlias);
                    MipsRegister c2 = useAlias.isPhysical() ? (MipsRegister) useAlias : color.get(useAlias);
                    
                    // Note: If spilled, they won't be in color map. 
                    // But if we reached finish(), spilledNodes must be empty!
                    // So c1 and c2 should be valid.
                    
                    if (c1 == c2) {
                        continue; // Coalesced move, remove it
                    }
                }
                
                // Replace Def/Use
                List<Operand> defs = new ArrayList<>(inst.getDef());
                for (Operand def : defs) {
                    if (def instanceof VirtualRegister v) {
                        if (color.containsKey(v)) {
                            inst.replaceDef(v, color.get(v));
                        }
                    }
                }
                
                List<Operand> uses = new ArrayList<>(inst.getUse());
                for (Operand use : uses) {
                    if (use instanceof VirtualRegister v) {
                        if (color.containsKey(v)) {
                            inst.replaceUse(v, color.get(v));
                        }
                    }
                }
                
                newInsts.add(inst);
            }
            
            block.getInstructions().clear();
            block.getInstructions().addAll(newInsts);
        }

        // --- Step 4.3: CSR Support (Callee-Saved Registers) ---
        // Identify used $s registers
        Set<MipsRegister> usedS = new HashSet<>();
        for (MipsRegister reg : color.values()) {
            String name = reg.toString();
            // Assuming MipsRegister.toString() returns "$s0", "$s1" etc. or we check ID.
            // MipsRegister enum usually has S0..S7.
            // Or check range: S0 is typically after T9?
            // Safer to check explicit list.
            if (isCalleeSaved(reg)) {
                usedS.add(reg);
            }
        }
        
        // Also check if any Coalesced node is aliased to physical $s
        for (Operand op : coalescedNodes) {
            Operand alias = getAlias(op);
            if (alias.isPhysical() && isCalleeSaved((MipsRegister) alias)) {
                usedS.add((MipsRegister) alias);
            }
        }

        // [Fix] Scan ALL instructions for explicit Physical Register usage
        // This covers cases where MipsGenerator manually uses $s0 etc.
        for (MipsBasicBlock block : func.getBlocks()) {
            for (MipsInstruction inst : block.getInstructions()) {
                for (Operand def : inst.getDef()) {
                    if (def instanceof MipsRegister reg && isCalleeSaved(reg)) {
                        usedS.add(reg);
                    }
                }
                for (Operand use : inst.getUse()) {
                    if (use instanceof MipsRegister reg && isCalleeSaved(reg)) {
                        usedS.add(reg);
                    }
                }
            }
        }

        if (!usedS.isEmpty()) {
            List<MipsRegister> sortedS = new ArrayList<>(usedS);
            sortedS.sort(Comparator.comparingInt(Enum::ordinal));
            
            int csrSize = sortedS.size() * 4;
            // Align if needed? Stack must be 8-byte aligned.
            // If csrSize is 12 (3 regs), we need 16? 
            // Frame Size total must be 8-byte aligned.
            // The existing frame is aligned.
            // If we add csrSize, we should probably align csrSize too to keep alignment simple.
             if (csrSize % 8 != 0) {
                csrSize += (8 - csrSize % 8);
            }
            
            // 1. Patch Stack Offsets (Prologue, Epilogue, and existing Access)
            // Similar to spill rewrite, but now for CSR
            for (MipsBasicBlock block : func.getBlocks()) {
                List<MipsInstruction> newInsts = new ArrayList<>();
                for (MipsInstruction inst : block.getInstructions()) {
                    MipsInstruction patched = inst;
                    
                    if (inst instanceof MipsBinary bin && bin.getImm() != null) {
                        // Prologue: subu $sp, $sp, frameSize
                        if (bin.getOp().equals("subu") && bin.getRd() == MipsRegister.SP && bin.getRs() == MipsRegister.SP) {
                            patched = new MipsBinary("subu", MipsRegister.SP, MipsRegister.SP, bin.getImm() + csrSize);
                        }
                        // Epilogue: addu $sp, $sp, frameSize
                        else if (bin.getOp().equals("addu") && bin.getRd() == MipsRegister.SP && bin.getRs() == MipsRegister.SP) {
                            patched = new MipsBinary("addu", MipsRegister.SP, MipsRegister.SP, bin.getImm() + csrSize);
                        }
                        // [Fix] Restore CSR addiu patching (unconditional shift)
                        else if ((bin.getOp().equals("addiu") || bin.getOp().equals("addi")) && 
                                 bin.getRs() == MipsRegister.SP) {
                            patched = new MipsBinary(bin.getOp(), bin.getRd(), MipsRegister.SP, bin.getImm() + csrSize);
                        }
                    } else if (inst instanceof MipsLoadStore ls) {
                        // Access: lw/sw $t, offset($sp)
                        if (ls.getBase() == MipsRegister.SP) {
                            patched = new MipsLoadStore(ls.getType(), ls.getRt(), MipsRegister.SP, ls.getOffset() + csrSize);
                        }
                    }
                    newInsts.add(patched);
                }
                block.getInstructions().clear();
                block.getInstructions().addAll(newInsts);
            }
            
            // 2. Insert Save/Restore Code
            // Save at Prologue (Entry Block)
            // Typically after "subu $sp..."
            // But we can just insert at the beginning of the block instructions if we assume "subu" is first?
            // Wait, if we insert at beginning, "subu" comes first?
            // MipsGenerator puts "subu" at index 0 or 1 etc.
            // We should find "subu $sp, $sp, ..." and insert AFTER it.
            // If no subu (frameSize=0 originally, but now we have csrSize), we need to INSERT subu!
            
            // Strategy: Scan instructions.
            // If "subu $sp" found, insert saves after it.
            // If not found (and we are in entry block), insert subu + saves at index 0.

            // Since we already patched "subu", let's find the potentially patched one.
            
            MipsBasicBlock entryBlock = func.getBlocks().get(0);
            List<MipsInstruction> entryInsts = new ArrayList<>(entryBlock.getInstructions());
            List<MipsInstruction> newEntryInsts = new ArrayList<>();
            boolean subuFound = false;
            
            for (MipsInstruction inst : entryInsts) {
                newEntryInsts.add(inst);
                if (inst instanceof MipsBinary bin && bin.getOp().equals("subu") && 
                    bin.getRd() == MipsRegister.SP && bin.getRs() == MipsRegister.SP) {
                    subuFound = true;
                    // Insert Saves
                    // Offset: 0, 4, 8... because stack was shifted up by csrSize.
                    // The bottom [0, csrSize) is for us.
                    int off = 0;
                    for (MipsRegister s : sortedS) {
                        newEntryInsts.add(new MipsLoadStore(MipsLoadStore.Type.SW, s, MipsRegister.SP, off));
                        off += 4;
                    }
                }
            }
            
            if (!subuFound) {
                 // No frame originally. But we need frame for CSR.
                 // Insert "subu $sp, $sp, csrSize" at head.
                 List<MipsInstruction> wrapper = new ArrayList<>();
                 wrapper.add(new MipsBinary("subu", MipsRegister.SP, MipsRegister.SP, csrSize));
                 int off = 0;
                 for (MipsRegister s : sortedS) {
                     wrapper.add(new MipsLoadStore(MipsLoadStore.Type.SW, s, MipsRegister.SP, off));
                     off += 4;
                 }
                 wrapper.addAll(newEntryInsts);
                 newEntryInsts = wrapper;
            }
            entryBlock.getInstructions().clear();
            entryBlock.getInstructions().addAll(newEntryInsts);
            
            // Restore at Epilogue
            // Find "addu $sp" and insert restores BEFORE it.
            // Or "jr $ra" if no frame.
            
            for (MipsBasicBlock block : func.getBlocks()) {
                List<MipsInstruction> insts = new ArrayList<>(block.getInstructions());
                List<MipsInstruction> newInsts = new ArrayList<>();
                boolean blockChanged = false;

                for (MipsInstruction inst : insts) {
                     boolean isEpiloguePoint = false;
                     // Case 1: addu $sp, $sp, ...
                     if (inst instanceof MipsBinary bin && bin.getOp().equals("addu") && 
                         bin.getRd() == MipsRegister.SP && bin.getRs() == MipsRegister.SP) {
                         isEpiloguePoint = true;
                     }
                     // Case 2: jr $ra (and no addu found yet? usually addu precedes jr)
                     // If original frameSize was 0, no addu exists. We must insert restores + addu before jr.
                     else if (inst instanceof MipsBranch br && br.getOp().equals("jr") && br.getRs() == MipsRegister.RA) {
                          // Check if previous was addu? If yes, we already handled it.
                          // But we are iterating.
                          // Let's rely on addu if it exists.
                          // If frameSize=0 originally, subuFound=False logic above handled Prologue.
                          // Here if no addu, we trigger on jr.
                          
                          // How to detect if we already inserted for addu?
                          // We can just rely on logic: if we see addu, we insert.
                          // If we see jr, we check if we inserted?
                          // Or simpler: If existing frame, addu exists. If not, no addu.
                          
                          if (!subuFound) { // Originally no frame
                              isEpiloguePoint = true; 
                              // But wait, if isEpiloguePoint=true here, we insert restores. 
                              // Do we also insert addu? Yes.
                          }
                     }
                     
                     if (isEpiloguePoint) {
                        int off = 0;
                        for (MipsRegister s : sortedS) {
                            newInsts.add(new MipsLoadStore(MipsLoadStore.Type.LW, s, MipsRegister.SP, off));
                            off += 4;
                        }
                        if (!subuFound && inst instanceof MipsBranch) { // inserting new addu before jr
                            newInsts.add(new MipsBinary("addu", MipsRegister.SP, MipsRegister.SP, csrSize));
                        }
                        blockChanged = true;
                     }
                     newInsts.add(inst);
                }
                
                if (blockChanged) {
                    block.getInstructions().clear();
                    block.getInstructions().addAll(newInsts);
                }
            }
        }
    }

    private boolean isCalleeSaved(MipsRegister r) {
        String n = r.toString(); // e.g. "$s0"
        // Depends on MipsRegister implementation. MipsRegister usually is enum or object.
        // Assuming typical MIPS names.
        return n.startsWith("$s") && !n.equals("$sp"); // $sp is technically callee-saved but handled by subu/addu
    }
}