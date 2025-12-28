package io.github.tomorrow615.compiler.backend.mips.analysis;

import io.github.tomorrow615.compiler.backend.mips.structure.MipsBasicBlock;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;

import java.util.*;

/**
 * 循环分析
 * 用于计算每个基本块的循环深度，以便在寄存器分配时计算溢出代价。
 * 公式：Cost = (Def + Use) * 10^Depth
 */
public class LoopInfo {
    private final MipsFunction function;
    private final Map<MipsBasicBlock, Integer> loopDepthMap;

    public LoopInfo(MipsFunction function) {
        this.function = function;
        this.loopDepthMap = new HashMap<>();
        compute();
    }

    public int getLoopDepth(MipsBasicBlock block) {
        return loopDepthMap.getOrDefault(block, 0);
    }

    private void compute() {
        // 确保 CFG 已建立
        function.buildCFG();
        
        List<MipsBasicBlock> blocks = function.getBlocks();
        if (blocks.isEmpty()) return;

        // 1. 计算支配者信息 (Dominators)
        // doms[b] = set of blocks that dominate b
        Map<MipsBasicBlock, Set<MipsBasicBlock>> doms = computeDominators(blocks);

        // 2. 识别自然循环 (Natural Loops)
        // Key: Header Block, Value: Set of Latch Blocks (back-edges)
        Map<MipsBasicBlock, Set<MipsBasicBlock>> loopHeaders = new HashMap<>();
        
        for (MipsBasicBlock block : blocks) {
            for (MipsBasicBlock pred : block.getPredecessors()) {
                // 如果 block 支配 pred，则是回边 (pred -> block)
                if (doms.get(pred).contains(block)) {
                    loopHeaders.computeIfAbsent(block, k -> new HashSet<>()).add(pred);
                }
            }
        }

        // 3. 构建循环体
        List<Set<MipsBasicBlock>> allLoops = new ArrayList<>();
        for (Map.Entry<MipsBasicBlock, Set<MipsBasicBlock>> entry : loopHeaders.entrySet()) {
            MipsBasicBlock header = entry.getKey();
            Set<MipsBasicBlock> latches = entry.getValue();
            allLoops.add(constructNaturalLoop(header, latches));
        }

        // 4. 计算深度 (一个块属于多少个循环)
        for (MipsBasicBlock block : blocks) {
            int depth = 0;
            for (Set<MipsBasicBlock> loop : allLoops) {
                if (loop.contains(block)) {
                    depth++;
                }
            }
            loopDepthMap.put(block, depth);
        }
    }

    /**
     * 计算支配节点 (Iterative Algorithm)
     */
    private Map<MipsBasicBlock, Set<MipsBasicBlock>> computeDominators(List<MipsBasicBlock> blocks) {
        Map<MipsBasicBlock, Set<MipsBasicBlock>> doms = new HashMap<>();
        MipsBasicBlock entry = blocks.get(0);

        // 初始化
        Set<MipsBasicBlock> allBlocks = new HashSet<>(blocks);
        for (MipsBasicBlock block : blocks) {
            if (block == entry) {
                Set<MipsBasicBlock> entryDom = new HashSet<>();
                entryDom.add(entry);
                doms.put(entry, entryDom);
            } else {
                doms.put(block, new HashSet<>(allBlocks));
            }
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (MipsBasicBlock block : blocks) {
                if (block == entry) continue;

                // NewDom = {block} U Intersection(Doms(pred))
                Set<MipsBasicBlock> newDom = null;
                for (MipsBasicBlock pred : block.getPredecessors()) {
                    if (doms.containsKey(pred)) {
                        if (newDom == null) {
                            newDom = new HashSet<>(doms.get(pred));
                        } else {
                            newDom.retainAll(doms.get(pred));
                        }
                    }
                }
                
                // 处理不可达块的情况 (可能没有前驱，或者前驱未被处理)
                if (newDom == null) {
                    newDom = new HashSet<>(); 
                    // 理论上不可达块不应该影响 Loop Analysis 的关键路径
                }
                
                newDom.add(block);

                if (!newDom.equals(doms.get(block))) {
                    doms.put(block, newDom);
                    changed = true;
                }
            }
        }
        return doms;
    }

    /**
     * 构建自然循环
     * 从 latches 开始反向遍历，直到遇到 header
     */
    private Set<MipsBasicBlock> constructNaturalLoop(MipsBasicBlock header, Set<MipsBasicBlock> latches) {
        Set<MipsBasicBlock> loopNodes = new HashSet<>();
        loopNodes.add(header);

        Queue<MipsBasicBlock> worklist = new LinkedList<>(latches);
        for (MipsBasicBlock latch : latches) {
            loopNodes.add(latch);
        }

        while (!worklist.isEmpty()) {
            MipsBasicBlock current = worklist.poll();
            for (MipsBasicBlock pred : current.getPredecessors()) {
                if (!loopNodes.contains(pred)) {
                    loopNodes.add(pred);
                    worklist.add(pred);
                }
            }
        }
        return loopNodes;
    }
}
