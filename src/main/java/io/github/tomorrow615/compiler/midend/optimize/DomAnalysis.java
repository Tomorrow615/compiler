package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 支配树分析 Pass
 * 计算每个基本块的：
 * 1. IDom (Immediate Dominator): 直接支配者
 * 2. DF (Dominance Frontier): 支配边界
 * 
 * 这是实现 Mem2Reg (SSA 构建) 的前置依赖
 */
public class DomAnalysis implements Pass {
    // === 核心数据结构 ===
    
    // 每个块的直接支配者
    private Map<BasicBlock, BasicBlock> idomMap;
    
    // 支配树的邻接表 (父 → 子列表)
    private Map<BasicBlock, List<BasicBlock>> domTreeChildren;
    
    // 支配边界
    private Map<BasicBlock, Set<BasicBlock>> domFrontier;
    
    // === 辅助数据结构 ===
    
    // 逆后序遍历 (Reverse Post Order) 的序列
    private List<BasicBlock> rpo;
    
    // 块到 RPO 索引的映射
    private Map<BasicBlock, Integer> rpoIndex;

    @Override
    public String getName() {
        return "DominatorAnalysis";
    }

    @Override
    public void runOnFunction(Function func) {
        if (func.isDeclaration()) {
            return;
        }
        
        // 1. 计算 RPO
        computeRPO(func);
        
        // 2. 计算 IDom
        idomMap = new HashMap<>();
        computeIDom();
        
        // 3. 构建支配树
        buildDomTree();
        
        // 4. 计算 DF
        computeDF();
    }

    // ==================== RPO 计算 ====================
    
    /**
     * 计算逆后序遍历 (Reverse Post Order)
     */
    private void computeRPO(Function func) {
        List<BasicBlock> postOrder = new ArrayList<>();
        Set<BasicBlock> visited = new HashSet<>();
        
        // 从 Entry Block 开始 DFS
        BasicBlock entry = func.getBasicBlocks().get(0);
        dfsPostOrder(entry, visited, postOrder);
        
        // 反转得到 RPO
        Collections.reverse(postOrder);
        this.rpo = postOrder;
        
        // 构建索引映射
        this.rpoIndex = new HashMap<>();
        for (int i = 0; i < rpo.size(); i++) {
            rpoIndex.put(rpo.get(i), i);
        }
    }
    
    /**
     * DFS 后序遍历
     */
    private void dfsPostOrder(BasicBlock bb, Set<BasicBlock> visited, List<BasicBlock> postOrder) {
        if (visited.contains(bb)) {
            return;
        }
        visited.add(bb);
        
        // 先访问所有后继
        for (BasicBlock succ : bb.getSuccessors()) {
            dfsPostOrder(succ, visited, postOrder);
        }
        
        // 后序：在所有子节点访问后再加入
        postOrder.add(bb);
    }

    // ==================== IDom 计算 (Cooper's Algorithm) ====================
    
    /**
     * 计算直接支配者 (Immediate Dominator)
     * 使用 Cooper's Algorithm (迭代数据流)
     */
    private void computeIDom() {
        BasicBlock entry = rpo.get(0);
        idomMap.put(entry, entry);  // Entry 支配自己
        
        boolean changed = true;
        while (changed) {
            changed = false;
            
            // 遍历 RPO (跳过 Entry)
            for (int i = 1; i < rpo.size(); i++) {
                BasicBlock b = rpo.get(i);
                
                // 找第一个已处理的前驱
                BasicBlock newIdom = null;
                for (BasicBlock pred : b.getPredecessors()) {
                    if (idomMap.containsKey(pred)) {
                        newIdom = pred;
                        break;
                    }
                }
                
                // 如果没有已处理的前驱，跳过（不可达块）
                if (newIdom == null) {
                    continue;
                }
                
                // 与其他已处理前驱求交集
                for (BasicBlock pred : b.getPredecessors()) {
                    if (pred == newIdom) {
                        continue;
                    }
                    if (idomMap.containsKey(pred)) {
                        newIdom = intersect(newIdom, pred);
                    }
                }
                
                // 检查是否有变化
                if (idomMap.get(b) != newIdom) {
                    idomMap.put(b, newIdom);
                    changed = true;
                }
            }
        }
    }
    
    /**
     * 求两个块在支配树上的最近公共祖先 (LCA)
     */
    private BasicBlock intersect(BasicBlock b1, BasicBlock b2) {
        while (b1 != b2) {
            while (rpoIndex.get(b1) > rpoIndex.get(b2)) {
                b1 = idomMap.get(b1);
            }
            while (rpoIndex.get(b2) > rpoIndex.get(b1)) {
                b2 = idomMap.get(b2);
            }
        }
        return b1;
    }

    // ==================== 支配树构建 ====================
    
    /**
     * 构建支配树的邻接表表示
     */
    private void buildDomTree() {
        domTreeChildren = new HashMap<>();
        
        for (Map.Entry<BasicBlock, BasicBlock> entry : idomMap.entrySet()) {
            BasicBlock child = entry.getKey();
            BasicBlock parent = entry.getValue();
            
            // Entry 的 IDom 是自己，跳过
            if (child == parent) {
                continue;
            }
            
            domTreeChildren.putIfAbsent(parent, new ArrayList<>());
            domTreeChildren.get(parent).add(child);
        }
    }

    // ==================== DF 计算 ====================
    
    /**
     * 计算支配边界 (Dominance Frontier)
     * DF(X) = {Y | X 支配 Y 的某个前驱，但 X 不严格支配 Y}
     */
    private void computeDF() {
        domFrontier = new HashMap<>();
        
        // 初始化所有块的 DF 为空集
        for (BasicBlock b : rpo) {
            domFrontier.put(b, new HashSet<>());
        }
        
        // 遍历所有块
        for (BasicBlock b : rpo) {
            // 只有汇合点（多个前驱）才可能出现在 DF 中
            if (b.getPredecessors().size() < 2) {
                continue;
            }
            
            // 遍历每个前驱
            for (BasicBlock pred : b.getPredecessors()) {
                BasicBlock runner = pred;
                
                // 沿支配树向上回溯，直到到达 b 的 IDom
                BasicBlock bIdom = idomMap.get(b);
                while (runner != bIdom) {
                    domFrontier.get(runner).add(b);
                    runner = idomMap.get(runner);
                }
            }
        }
    }

    // ==================== 对外接口 ====================
    
    /**
     * 获取某个块的直接支配者
     */
    public BasicBlock getIDom(BasicBlock bb) {
        return idomMap.get(bb);
    }
    
    /**
     * 获取某个块在支配树中的子节点
     */
    public List<BasicBlock> getDomTreeChildren(BasicBlock bb) {
        return domTreeChildren.getOrDefault(bb, Collections.emptyList());
    }
    
    /**
     * 获取某个块的支配边界
     */
    public Set<BasicBlock> getDominanceFrontier(BasicBlock bb) {
        return domFrontier.getOrDefault(bb, Collections.emptySet());
    }

    // ==================== 调试输出 ====================
    
    /**
     * 打印支配树信息（用于调试）
     */
    public void printDomInfo(Function func) {
        System.out.println("=== Dominator Info for " + func.getName() + " ===");
        
        for (BasicBlock bb : rpo) {
            System.out.println("Block: " + bb.getName());
            BasicBlock idom = idomMap.get(bb);
            System.out.println("  IDom: " + (idom == bb ? "ENTRY" : idom.getName()));
            
            Set<BasicBlock> df = domFrontier.get(bb);
            if (!df.isEmpty()) {
                String dfStr = df.stream()
                    .map(BasicBlock::getName)
                    .collect(Collectors.joining(", "));
                System.out.println("  DF: " + dfStr);
            }
        }
        System.out.println();
    }
}
