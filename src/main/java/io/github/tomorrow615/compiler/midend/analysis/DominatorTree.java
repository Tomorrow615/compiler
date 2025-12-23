package io.github.tomorrow615.compiler.midend.analysis;

import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;

import java.util.*;

/**
 * 支配树分析器
 * 计算：
 * 1. 不可达块清理
 * 2. 直接支配者 (IDom)
 * 3. 支配边界 (Dominance Frontier)
 *
 * 算法：Iterative Dominator Algorithm (Cooper-Harvey-Kennedy)
 */
public class DominatorTree {
    private final Function function;
    private final BasicBlock entryBlock;

    // 可达块集合 (从 Entry 可达)
    private final Set<BasicBlock> reachableBlocks;

    // IDom: 直接支配者 (Entry 的 IDom 为 null)
    private final Map<BasicBlock, BasicBlock> idomMap;

    // 支配树子节点
    private final Map<BasicBlock, List<BasicBlock>> domTreeChildren;

    // 支配边界
    private final Map<BasicBlock, Set<BasicBlock>> dominanceFrontier;

    public DominatorTree(Function function) {
        this.function = function;
        this.entryBlock = function.getBasicBlocks().isEmpty() ? null : function.getBasicBlocks().get(0);
        this.reachableBlocks = new HashSet<>();
        this.idomMap = new HashMap<>();
        this.domTreeChildren = new HashMap<>();
        this.dominanceFrontier = new HashMap<>();

        if (entryBlock != null) {
            analyze();
        }
    }

    /**
     * 核心分析入口
     */
    private void analyze() {
        // Step 1: 找出所有可达块
        computeReachableBlocks();

        // Step 2: 计算 IDom (迭代算法)
        computeImmediateDominators();

        // Step 3: 构建支配树子节点
        buildDomTreeChildren();

        // Step 4: 计算支配边界
        computeDominanceFrontiers();
    }

    // ========== Step 1: 可达块分析 ==========

    private void computeReachableBlocks() {
        Queue<BasicBlock> worklist = new LinkedList<>();
        worklist.add(entryBlock);
        reachableBlocks.add(entryBlock);

        while (!worklist.isEmpty()) {
            BasicBlock current = worklist.poll();
            for (BasicBlock succ : current.getSuccessors()) {
                if (!reachableBlocks.contains(succ)) {
                    reachableBlocks.add(succ);
                    worklist.add(succ);
                }
            }
        }
    }

    // ========== Step 2: IDom 计算 (Iterative Algorithm) ==========

    /**
     * 迭代计算直接支配者
     * 原理：Dom(n) = {n} ∪ (∩ Dom(p) for all p ∈ preds(n))
     * IDom(n) = Dom(n) 中离 n 最近的那个（即支配集中除 n 外最"小"的）
     */
    private void computeImmediateDominators() {
        // 初始化：Entry 支配自己，其他块的支配集为全体可达块
        Map<BasicBlock, Set<BasicBlock>> domSets = new HashMap<>();
        for (BasicBlock bb : reachableBlocks) {
            if (bb == entryBlock) {
                domSets.put(bb, new HashSet<>(Collections.singleton(bb)));
            } else {
                domSets.put(bb, new HashSet<>(reachableBlocks)); // 初始化为全集
            }
        }

        // 迭代直到不再变化
        boolean changed = true;
        while (changed) {
            changed = false;
            for (BasicBlock bb : reachableBlocks) {
                if (bb == entryBlock) continue;

                // 计算所有前驱支配集的交集
                Set<BasicBlock> newDom = null;
                for (BasicBlock pred : bb.getPredecessors()) {
                    if (!reachableBlocks.contains(pred)) continue; // 跳过不可达前驱
                    if (newDom == null) {
                        newDom = new HashSet<>(domSets.get(pred));
                    } else {
                        newDom.retainAll(domSets.get(pred));
                    }
                }

                if (newDom == null) {
                    newDom = new HashSet<>();
                }
                newDom.add(bb); // Dom(n) 必须包含 n 自己

                if (!newDom.equals(domSets.get(bb))) {
                    domSets.put(bb, newDom);
                    changed = true;
                }
            }
        }

        // 从 domSets 提取 IDom
        // IDom(n) = Dom(n) - {n} 中，唯一支配 n 且不支配 Dom(n) 中其他人的
        // 简化方法：Dom(n) - {n} 中，支配集最大的那个就是 IDom
        for (BasicBlock bb : reachableBlocks) {
            if (bb == entryBlock) {
                idomMap.put(bb, null); // Entry 没有 IDom
                continue;
            }

            Set<BasicBlock> dominators = new HashSet<>(domSets.get(bb));
            dominators.remove(bb); // 移除自己

            // IDom 是 dominators 中，被其他所有 dominators 支配的那个
            // 即：IDom 的支配集最大（包含最多元素）
            BasicBlock idom = null;
            int maxDomSetSize = -1;
            for (BasicBlock candidate : dominators) {
                int size = domSets.get(candidate).size();
                if (size > maxDomSetSize) {
                    maxDomSetSize = size;
                    idom = candidate;
                }
            }
            idomMap.put(bb, idom);
        }
    }

    // ========== Step 3: 构建支配树子节点 ==========

    private void buildDomTreeChildren() {
        for (BasicBlock bb : reachableBlocks) {
            domTreeChildren.put(bb, new ArrayList<>());
        }

        for (BasicBlock bb : reachableBlocks) {
            BasicBlock idom = idomMap.get(bb);
            if (idom != null) {
                domTreeChildren.get(idom).add(bb);
            }
        }
    }

    // ========== Step 4: 计算支配边界 ==========

    /**
     * 支配边界定义：
     * X 的支配边界 DF(X) 包含 Y，当且仅当：
     * 1. X 支配 Y 的某个前驱
     * 2. X 不严格支配 Y（即 X != Y 且 X 不支配 Y）
     *
     * 算法（自底向上）：
     * 对于每个有多个前驱的块 Y（汇合点），
     * 从 Y 的每个前驱 P 开始，向上回溯到 IDom(Y)（不包含），
     * 途中的每个块 X 都把 Y 加入 DF(X)
     */
    private void computeDominanceFrontiers() {
        for (BasicBlock bb : reachableBlocks) {
            dominanceFrontier.put(bb, new HashSet<>());
        }

        for (BasicBlock bb : reachableBlocks) {
            List<BasicBlock> preds = bb.getPredecessors();
            // 只处理可达的前驱
            List<BasicBlock> reachablePreds = new ArrayList<>();
            for (BasicBlock p : preds) {
                if (reachableBlocks.contains(p)) {
                    reachablePreds.add(p);
                }
            }

            if (reachablePreds.size() >= 2) {
                // bb 是汇合点
                for (BasicBlock pred : reachablePreds) {
                    BasicBlock runner = pred;
                    // 向上回溯直到 IDom(bb)
                    while (runner != null && runner != idomMap.get(bb)) {
                        dominanceFrontier.get(runner).add(bb);
                        runner = idomMap.get(runner);
                    }
                }
            }
        }
    }

    // ========== 公开 API ==========

    public BasicBlock getIDom(BasicBlock bb) {
        return idomMap.get(bb);
    }

    public List<BasicBlock> getDomTreeChildren(BasicBlock bb) {
        return domTreeChildren.getOrDefault(bb, Collections.emptyList());
    }

    public Set<BasicBlock> getDominanceFrontier(BasicBlock bb) {
        return dominanceFrontier.getOrDefault(bb, Collections.emptySet());
    }

    public Set<BasicBlock> getReachableBlocks() {
        return Collections.unmodifiableSet(reachableBlocks);
    }

    public BasicBlock getEntryBlock() {
        return entryBlock;
    }

    /**
     * 检查 a 是否支配 b
     */
    public boolean dominates(BasicBlock a, BasicBlock b) {
        if (a == b) return true;
        BasicBlock runner = b;
        while (runner != null) {
            if (runner == a) return true;
            runner = idomMap.get(runner);
        }
        return false;
    }

    /**
     * 检查 a 是否严格支配 b (a != b 且 a 支配 b)
     */
    public boolean strictlyDominates(BasicBlock a, BasicBlock b) {
        return a != b && dominates(a, b);
    }
}
