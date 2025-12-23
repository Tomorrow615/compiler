package io.github.tomorrow615.compiler.midend.analysis;

import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;

import java.util.*;

/**
 * [Phase 1.2] 循环信息数据结构
 * 
 * 存储一个自然循环 (Natural Loop) 的所有信息：
 * - Header: 循环头 (唯一入口，回边的目标)
 * - Latches: 回边的源块 (可能有多个)
 * - Blocks: 循环体内的所有基本块
 * - Exits: 循环的出口块
 * - Parent/Children: 循环嵌套关系
 */
public class LoopInfo {
    // 循环头：唯一的入口点，也是回边的目标
    private final BasicBlock header;
    
    // 回边的源块（可能有多个，如 continue 语句产生的多个回边）
    private final Set<BasicBlock> latches;
    
    // 循环体内的所有基本块
    private final Set<BasicBlock> blocks;
    
    // 循环出口块（循环外的第一个块）
    private final Set<BasicBlock> exitBlocks;
    
    // Pre-Header：循环的唯一非循环前驱（由 LICM 创建，初始为 null）
    private BasicBlock preHeader;
    
    // 父循环（嵌套关系）
    private LoopInfo parentLoop;
    
    // 子循环
    private final List<LoopInfo> subLoops;
    
    // 循环深度（最外层为 1）
    private int depth;
    
    public LoopInfo(BasicBlock header) {
        this.header = header;
        this.latches = new HashSet<>();
        this.blocks = new HashSet<>();
        this.exitBlocks = new HashSet<>();
        this.subLoops = new ArrayList<>();
        this.depth = 1;
        
        // Header 总是属于循环体
        this.blocks.add(header);
    }
    
    // ========== 构建方法 ==========
    
    /**
     * 添加一个回边源块
     */
    public void addLatch(BasicBlock latch) {
        latches.add(latch);
        blocks.add(latch);
    }
    
    /**
     * 添加循环体块
     */
    public void addBlock(BasicBlock block) {
        blocks.add(block);
    }
    
    /**
     * 添加出口块
     */
    public void addExitBlock(BasicBlock exit) {
        exitBlocks.add(exit);
    }
    
    /**
     * 设置父循环
     */
    public void setParentLoop(LoopInfo parent) {
        this.parentLoop = parent;
        if (parent != null) {
            parent.subLoops.add(this);
            this.depth = parent.depth + 1;
        }
    }
    
    /**
     * 设置/获取 Pre-Header
     */
    public void setPreHeader(BasicBlock preHeader) {
        this.preHeader = preHeader;
    }
    
    public BasicBlock getPreHeader() {
        return preHeader;
    }
    
    // ========== 查询方法 ==========
    
    public BasicBlock getHeader() {
        return header;
    }
    
    public Set<BasicBlock> getLatches() {
        return Collections.unmodifiableSet(latches);
    }
    
    public Set<BasicBlock> getBlocks() {
        return Collections.unmodifiableSet(blocks);
    }
    
    public Set<BasicBlock> getExitBlocks() {
        return Collections.unmodifiableSet(exitBlocks);
    }
    
    public LoopInfo getParentLoop() {
        return parentLoop;
    }
    
    public List<LoopInfo> getSubLoops() {
        return Collections.unmodifiableList(subLoops);
    }
    
    public int getDepth() {
        return depth;
    }
    
    /**
     * 检查块是否在循环内
     */
    public boolean contains(BasicBlock block) {
        return blocks.contains(block);
    }
    
    /**
     * 检查是否为最内层循环（无子循环）
     */
    public boolean isInnermost() {
        return subLoops.isEmpty();
    }
    
    /**
     * 获取循环的唯一非循环前驱（如果存在）
     * 用于判断是否需要插入 Pre-Header
     */
    public BasicBlock getLoopPredeccessor() {
        List<BasicBlock> nonLoopPreds = new ArrayList<>();
        for (BasicBlock pred : header.getPredecessors()) {
            if (!blocks.contains(pred)) {
                nonLoopPreds.add(pred);
            }
        }
        // 只有唯一的非循环前驱时才返回
        return nonLoopPreds.size() == 1 ? nonLoopPreds.get(0) : null;
    }
    
    @Override
    public String toString() {
        return "Loop[header=" + header.getName() + 
               ", depth=" + depth + 
               ", blocks=" + blocks.size() + "]";
    }
}
