package io.github.tomorrow615.compiler.midend.analysis;

import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;

import java.util.*;

/**
 * [Phase 1.2] 循环分析器
 * 
 * 功能：
 * 1. 识别回边 (Back Edge): A -> B 且 B 支配 A
 * 2. 构建自然循环 (Natural Loop)
 * 3. 计算循环嵌套关系和深度
 * 4. 标记每个 BasicBlock 的循环深度
 */
public class LoopAnalysis {
    private final Function function;
    private final DominatorTree domTree;
    
    // 所有识别的顶层循环（不包含被嵌套的子循环）
    private final List<LoopInfo> topLevelLoops;
    
    // 所有循环（包含嵌套的）
    private final List<LoopInfo> allLoops;
    
    // 每个基本块所属的最内层循环（没有循环则为 null）
    private final Map<BasicBlock, LoopInfo> blockToLoop;
    
    // 每个基本块的循环深度（不在循环中为 0）
    private final Map<BasicBlock, Integer> blockDepth;
    
    public LoopAnalysis(Function function) {
        this.function = function;
        this.domTree = new DominatorTree(function);
        this.topLevelLoops = new ArrayList<>();
        this.allLoops = new ArrayList<>();
        this.blockToLoop = new HashMap<>();
        this.blockDepth = new HashMap<>();
        
        analyze();
    }
    
    /**
     * 使用已有的支配树构造
     */
    public LoopAnalysis(Function function, DominatorTree domTree) {
        this.function = function;
        this.domTree = domTree;
        this.topLevelLoops = new ArrayList<>();
        this.allLoops = new ArrayList<>();
        this.blockToLoop = new HashMap<>();
        this.blockDepth = new HashMap<>();
        
        analyze();
    }
    
    /**
     * 核心分析入口
     */
    private void analyze() {
        // Step 1: 找出所有回边并构建基础循环
        Map<BasicBlock, LoopInfo> headerToLoop = findBackEdgesAndBuildLoops();
        
        // Step 2: 确定循环嵌套关系
        buildLoopNesting(headerToLoop);
        
        // Step 3: 计算每个块的循环深度
        computeBlockDepths();
    }
    
    // ========== Step 1: 识别回边并构建循环 ==========
    
    /**
     * 识别回边: 边 A -> B 是回边当且仅当 B 支配 A
     * 对于每个回边，构建对应的自然循环
     */
    private Map<BasicBlock, LoopInfo> findBackEdgesAndBuildLoops() {
        Map<BasicBlock, LoopInfo> headerToLoop = new HashMap<>();
        
        for (BasicBlock block : domTree.getReachableBlocks()) {
            for (BasicBlock succ : block.getSuccessors()) {
                // 检查是否为回边: succ 支配 block
                if (domTree.dominates(succ, block)) {
                    // 找到回边: block -> succ
                    // succ 是循环头 (Header)，block 是回边源 (Latch)
                    
                    LoopInfo loop = headerToLoop.get(succ);
                    if (loop == null) {
                        loop = new LoopInfo(succ);
                        headerToLoop.put(succ, loop);
                        allLoops.add(loop);
                    }
                    
                    loop.addLatch(block);
                    
                    // 反向搜索构建循环体
                    buildLoopBody(loop, block);
                }
            }
        }
        
        // 计算每个循环的出口块
        for (LoopInfo loop : allLoops) {
            computeExitBlocks(loop);
        }
        
        return headerToLoop;
    }
    
    /**
     * 从 Latch 反向搜索，找到所有循环体内的块
     * 
     * 算法：从 Latch 开始，沿着前驱边反向搜索，
     * 所有能到达且不经过 Header 的块都属于循环体
     */
    private void buildLoopBody(LoopInfo loop, BasicBlock latch) {
        BasicBlock header = loop.getHeader();
        
        // 如果 latch 就是 header，只有 header 在循环中
        if (latch == header) {
            return;
        }
        
        // BFS/DFS 反向搜索
        Queue<BasicBlock> worklist = new LinkedList<>();
        Set<BasicBlock> visited = new HashSet<>();
        
        worklist.add(latch);
        visited.add(header); // Header 已经在循环中，不需要再处理
        visited.add(latch);
        
        while (!worklist.isEmpty()) {
            BasicBlock current = worklist.poll();
            loop.addBlock(current);
            
            for (BasicBlock pred : current.getPredecessors()) {
                if (!visited.contains(pred) && domTree.getReachableBlocks().contains(pred)) {
                    visited.add(pred);
                    worklist.add(pred);
                }
            }
        }
    }
    
    /**
     * 计算循环的出口块
     * 出口块：循环内块的后继中，不在循环内的块
     */
    private void computeExitBlocks(LoopInfo loop) {
        for (BasicBlock block : loop.getBlocks()) {
            for (BasicBlock succ : block.getSuccessors()) {
                if (!loop.contains(succ)) {
                    loop.addExitBlock(succ);
                }
            }
        }
    }
    
    // ========== Step 2: 确定循环嵌套关系 ==========
    
    /**
     * 确定循环嵌套关系
     * 
     * 规则：如果循环 A 的 Header 在循环 B 的 Blocks 中（且 A != B），
     * 则 A 是 B 的子循环
     */
    private void buildLoopNesting(Map<BasicBlock, LoopInfo> headerToLoop) {
        // 按循环体大小排序（小循环在前），便于确定嵌套关系
        allLoops.sort(Comparator.comparingInt(l -> l.getBlocks().size()));
        
        for (LoopInfo innerLoop : allLoops) {
            LoopInfo parentLoop = null;
            int minParentSize = Integer.MAX_VALUE;
            
            // 找最小的包含此循环 Header 的外层循环
            for (LoopInfo outerLoop : allLoops) {
                if (outerLoop == innerLoop) continue;
                
                if (outerLoop.contains(innerLoop.getHeader()) && 
                    outerLoop.getBlocks().size() > innerLoop.getBlocks().size() &&
                    outerLoop.getBlocks().size() < minParentSize) {
                    parentLoop = outerLoop;
                    minParentSize = outerLoop.getBlocks().size();
                }
            }
            
            if (parentLoop != null) {
                innerLoop.setParentLoop(parentLoop);
            } else {
                // 没有父循环，是顶层循环
                topLevelLoops.add(innerLoop);
            }
        }
        
        // 更新所有循环的深度
        updateLoopDepths();
    }
    
    /**
     * 递归更新循环深度
     */
    private void updateLoopDepths() {
        for (LoopInfo loop : topLevelLoops) {
            updateDepthRecursive(loop, 1);
        }
    }
    
    private void updateDepthRecursive(LoopInfo loop, int depth) {
        // LoopInfo 内部的 depth 通过 setParentLoop 已经设置
        // 这里只需要更新 blockToLoop 映射
        for (BasicBlock block : loop.getBlocks()) {
            // 只有当块还没有被更深的循环标记时才更新
            LoopInfo existing = blockToLoop.get(block);
            if (existing == null || existing.getDepth() < loop.getDepth()) {
                blockToLoop.put(block, loop);
            }
        }
        
        for (LoopInfo subLoop : loop.getSubLoops()) {
            updateDepthRecursive(subLoop, depth + 1);
        }
    }
    
    // ========== Step 3: 计算块深度 ==========
    
    /**
     * 计算每个 BasicBlock 的循环深度
     * 不在任何循环中的块深度为 0
     */
    private void computeBlockDepths() {
        for (BasicBlock block : function.getBasicBlocks()) {
            LoopInfo loop = blockToLoop.get(block);
            if (loop != null) {
                blockDepth.put(block, loop.getDepth());
            } else {
                blockDepth.put(block, 0);
            }
        }
    }
    
    // ========== 公开 API ==========
    
    /**
     * 获取所有顶层循环（不被其他循环包含）
     */
    public List<LoopInfo> getTopLevelLoops() {
        return Collections.unmodifiableList(topLevelLoops);
    }
    
    /**
     * 获取所有循环
     */
    public List<LoopInfo> getAllLoops() {
        return Collections.unmodifiableList(allLoops);
    }
    
    /**
     * 获取块所属的最内层循环（如果有）
     */
    public LoopInfo getLoopFor(BasicBlock block) {
        return blockToLoop.get(block);
    }
    
    /**
     * 获取块的循环深度
     */
    public int getLoopDepth(BasicBlock block) {
        return blockDepth.getOrDefault(block, 0);
    }
    
    /**
     * 检查块是否在循环中
     */
    public boolean isInLoop(BasicBlock block) {
        return blockToLoop.containsKey(block);
    }
    
    /**
     * 检查块是否在指定循环中
     */
    public boolean isInLoop(BasicBlock block, LoopInfo loop) {
        return loop.contains(block);
    }
    
    /**
     * 获取使用的支配树
     */
    public DominatorTree getDominatorTree() {
        return domTree;
    }
    
    /**
     * 调试：打印所有循环信息
     */
    public void dump() {
        System.out.println("=== Loop Analysis for " + function.getName() + " ===");
        System.out.println("Found " + allLoops.size() + " loops:");
        for (LoopInfo loop : allLoops) {
            System.out.println("  " + loop);
            System.out.println("    Latches: " + loop.getLatches());
            System.out.println("    Exits: " + loop.getExitBlocks());
            if (loop.getParentLoop() != null) {
                System.out.println("    Parent: " + loop.getParentLoop().getHeader().getName());
            }
        }
    }
}
