package io.github.tomorrow615.compiler.midend.analysis;

import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;
import io.github.tomorrow615.compiler.midend.optimize.Pass;

import java.util.*;

/**
 * 支配树分析
 * 
 * 计算每个基本块的：
 * - idom: 直接支配者 (immediate dominator)
 * - domFrontier: 支配边界 (dominance frontier)
 * 
 * 支配关系: A dom B 表示从入口到 B 的每条路径都经过 A
 * 直接支配者: idom(B) 是 B 的所有支配者中，最接近 B 的那个
 * 支配边界: DF(A) = { B | A dom pred(B) 但 A 不严格支配 B }
 */
public class DominatorTree implements Pass {
    
    // 支配树结果，可被其他 Pass 查询
    private Map<BasicBlock, BasicBlock> idom = new HashMap<>();
    private Map<BasicBlock, Set<BasicBlock>> domFrontier = new HashMap<>();
    private Map<BasicBlock, Set<BasicBlock>> children = new HashMap<>(); // 支配树子节点
    
    @Override
    public String getName() {
        return "DominatorTree";
    }

    @Override
    public void runOnFunction(Function function) {
        List<BasicBlock> blocks = function.getBasicBlocks();
        if (blocks.isEmpty()) {
            return;
        }
        
        BasicBlock entry = blocks.get(0);
        
        // 初始化
        idom.clear();
        domFrontier.clear();
        children.clear();
        
        for (BasicBlock bb : blocks) {
            idom.put(bb, null);
            domFrontier.put(bb, new HashSet<>());
            children.put(bb, new HashSet<>());
        }
        
        // 入口块的 idom 是自己
        idom.put(entry, entry);
        
        // 计算 idom（使用简单迭代算法）
        computeIDom(blocks, entry);
        
        // 构建支配树子节点关系
        buildDomTree(blocks, entry);
        
        // 计算支配边界
        computeDomFrontier(blocks);
    }
    
    /**
     * 使用迭代数据流算法计算 idom
     */
    private void computeIDom(List<BasicBlock> blocks, BasicBlock entry) {
        // 计算逆后序遍历
        List<BasicBlock> rpo = reversePostOrder(blocks, entry);
        
        boolean changed = true;
        while (changed) {
            changed = false;
            
            for (BasicBlock bb : rpo) {
                if (bb == entry) continue;
                
                BasicBlock newIdom = null;
                
                // 遍历所有已处理的前驱
                for (BasicBlock pred : bb.getPredecessors()) {
                    if (idom.get(pred) != null) {
                        if (newIdom == null) {
                            newIdom = pred;
                        } else {
                            newIdom = intersect(newIdom, pred, rpo);
                        }
                    }
                }
                
                if (newIdom != null && idom.get(bb) != newIdom) {
                    idom.put(bb, newIdom);
                    changed = true;
                }
            }
        }
    }
    
    /**
     * 计算两个节点在支配树上的最近公共祖先
     */
    private BasicBlock intersect(BasicBlock b1, BasicBlock b2, List<BasicBlock> rpo) {
        Map<BasicBlock, Integer> rpoIndex = new HashMap<>();
        for (int i = 0; i < rpo.size(); i++) {
            rpoIndex.put(rpo.get(i), i);
        }
        
        while (b1 != b2) {
            while (rpoIndex.getOrDefault(b1, -1) > rpoIndex.getOrDefault(b2, -1)) {
                b1 = idom.get(b1);
                if (b1 == null) return b2;
            }
            while (rpoIndex.getOrDefault(b2, -1) > rpoIndex.getOrDefault(b1, -1)) {
                b2 = idom.get(b2);
                if (b2 == null) return b1;
            }
        }
        return b1;
    }
    
    /**
     * 逆后序遍历
     */
    private List<BasicBlock> reversePostOrder(List<BasicBlock> blocks, BasicBlock entry) {
        Set<BasicBlock> visited = new HashSet<>();
        List<BasicBlock> postOrder = new ArrayList<>();
        
        dfsPostOrder(entry, visited, postOrder);
        
        // 添加未访问的块（可能是死代码）
        for (BasicBlock bb : blocks) {
            if (!visited.contains(bb)) {
                postOrder.add(bb);
            }
        }
        
        Collections.reverse(postOrder);
        return postOrder;
    }
    
    private void dfsPostOrder(BasicBlock bb, Set<BasicBlock> visited, List<BasicBlock> postOrder) {
        if (visited.contains(bb)) return;
        visited.add(bb);
        
        for (BasicBlock succ : bb.getSuccessors()) {
            dfsPostOrder(succ, visited, postOrder);
        }
        
        postOrder.add(bb);
    }
    
    /**
     * 构建支配树子节点关系
     */
    private void buildDomTree(List<BasicBlock> blocks, BasicBlock entry) {
        for (BasicBlock bb : blocks) {
            BasicBlock parent = idom.get(bb);
            if (parent != null && parent != bb) {
                children.get(parent).add(bb);
            }
        }
    }
    
    /**
     * 计算支配边界
     */
    private void computeDomFrontier(List<BasicBlock> blocks) {
        for (BasicBlock bb : blocks) {
            if (bb.getPredecessors().size() >= 2) {
                for (BasicBlock pred : bb.getPredecessors()) {
                    BasicBlock runner = pred;
                    while (runner != null && runner != idom.get(bb)) {
                        domFrontier.get(runner).add(bb);
                        runner = idom.get(runner);
                    }
                }
            }
        }
    }
    
    // === 查询接口 ===
    
    public BasicBlock getIDom(BasicBlock bb) {
        return idom.get(bb);
    }
    
    public Set<BasicBlock> getDomFrontier(BasicBlock bb) {
        return domFrontier.getOrDefault(bb, Collections.emptySet());
    }
    
    public Set<BasicBlock> getChildren(BasicBlock bb) {
        return children.getOrDefault(bb, Collections.emptySet());
    }
    
    /**
     * 判断 a 是否支配 b
     */
    public boolean dominates(BasicBlock a, BasicBlock b) {
        if (a == b) return true;
        
        BasicBlock current = b;
        while (current != null) {
            if (current == a) return true;
            BasicBlock parent = idom.get(current);
            if (parent == current) break; // 到达入口
            current = parent;
        }
        return false;
    }
}
