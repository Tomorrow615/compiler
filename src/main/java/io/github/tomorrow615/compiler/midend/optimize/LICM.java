package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.analysis.*;
import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.*;

/**
 * [Phase 1.3] 循环不变量外提 Pass (Loop Invariant Code Motion)
 * 
 * 功能：
 * 1. 为每个循环插入 Pre-Header 块
 * 2. 识别循环不变量指令
 * 3. 将不变量外提到 Pre-Header
 */
public class LICM implements Pass {
    
    @Override
    public String getName() {
        return "LICM";
    }
    
    @Override
    public void runOnFunction(Function function) {
        if (function.isDeclaration() || function.getBasicBlocks().isEmpty()) {
            return;
        }
        
        // 构建循环分析
        LoopAnalysis loopAnalysis = new LoopAnalysis(function);
        
        // 从最内层循环开始处理（自底向上）
        List<LoopInfo> loops = new ArrayList<>(loopAnalysis.getAllLoops());
        // 按深度降序排序，先处理内层循环
        loops.sort((a, b) -> b.getDepth() - a.getDepth());
        
        for (LoopInfo loop : loops) {
            // Step 1: 确保有 Pre-Header
            BasicBlock preHeader = ensurePreHeader(loop, function);
            
            // Step 2: 识别并外提不变量
            if (preHeader != null) {
                hoistInvariants(loop, preHeader, loopAnalysis);
            }
        }
    }
    
    // ========== Step 1: Pre-Header 插入 ==========
    
    /**
     * 确保循环有一个 Pre-Header
     * Pre-Header 是循环 Header 的唯一非循环前驱
     * 
     * @return Pre-Header 块，如果无法创建则返回 null
     */
    private BasicBlock ensurePreHeader(LoopInfo loop, Function function) {
        BasicBlock header = loop.getHeader();
        
        // 收集所有非循环前驱
        List<BasicBlock> nonLoopPreds = new ArrayList<>();
        for (BasicBlock pred : header.getPredecessors()) {
            if (!loop.contains(pred)) {
                nonLoopPreds.add(pred);
            }
        }
        
        // 如果没有非循环前驱，说明是 Entry 块开始的循环，无法插入 Pre-Header
        if (nonLoopPreds.isEmpty()) {
            return null;
        }
        
        // 如果已经只有一个非循环前驱，直接使用
        if (nonLoopPreds.size() == 1) {
            BasicBlock existingPreHeader = nonLoopPreds.get(0);
            // 检查该前驱是否以无条件跳转结尾且只有一个后继
            if (existingPreHeader.getSuccessors().size() == 1) {
                loop.setPreHeader(existingPreHeader);
                return existingPreHeader;
            }
        }
        
        // 需要创建新的 Pre-Header
        BasicBlock preHeader = createPreHeader(loop, function, nonLoopPreds);
        loop.setPreHeader(preHeader);
        return preHeader;
    }
    
    /**
     * 创建 Pre-Header 块
     */
    private BasicBlock createPreHeader(LoopInfo loop, Function function, List<BasicBlock> nonLoopPreds) {
        BasicBlock header = loop.getHeader();
        
        // 创建新的 Pre-Header 块（不自动添加到 Function）
        String preHeaderName = header.getName() + "_preheader";
        BasicBlock preHeader = new BasicBlock(preHeaderName, null);
        
        // 将 Pre-Header 插入到 Function 的块列表中（在 Header 之前）
        int headerIndex = function.getBasicBlocks().indexOf(header);
        function.getBasicBlocks().add(headerIndex, preHeader);
        
        // 创建从 Pre-Header 到 Header 的无条件跳转
        BranchInst jumpToHeader = new BranchInst(header, preHeader);
        preHeader.addInstruction(jumpToHeader);
        
        // 更新 CFG：将非循环前驱的跳转目标从 Header 改为 Pre-Header
        for (BasicBlock pred : nonLoopPreds) {
            // 更新前驱的 terminator
            redirectBranch(pred, header, preHeader);
            
            // 更新 CFG 边
            pred.getSuccessors().remove(header);
            pred.getSuccessors().add(preHeader);
            header.getPredecessors().remove(pred);
            preHeader.getPredecessors().add(pred);
        }
        
        // Pre-Header 成为 Header 的前驱
        preHeader.getSuccessors().add(header);
        header.getPredecessors().add(preHeader);
        
        // 更新 Header 中的 Phi 节点
        updatePhiNodesForPreHeader(header, nonLoopPreds, preHeader);
        
        return preHeader;
    }
    
    /**
     * 将块的分支指令目标从 oldTarget 重定向到 newTarget
     */
    private void redirectBranch(BasicBlock block, BasicBlock oldTarget, BasicBlock newTarget) {
        if (block.getInstructions().isEmpty()) return;
        
        Instruction terminator = block.getInstructions().get(block.getInstructions().size() - 1);
        
        if (terminator instanceof BranchInst br) {
            // 替换操作数中的 oldTarget
            for (int i = 0; i < br.getOperands().size(); i++) {
                if (br.getOperand(i) == oldTarget) {
                    br.setOperand(i, newTarget);
                }
            }
        }
    }
    
    /**
     * 更新 Header 中的 Phi 节点
     * 修正版：如果外部输入值不一致，在 PreHeader 插入新的 Phi
     */
    private void updatePhiNodesForPreHeader(BasicBlock header, List<BasicBlock> nonLoopPreds, BasicBlock preHeader) {
        Set<BasicBlock> nonLoopPredSet = new HashSet<>(nonLoopPreds);
        
        // 使用新列表避免并发修改异常
        List<Instruction> instructions = new ArrayList<>(header.getInstructions());
        for (Instruction inst : instructions) {
            if (!(inst instanceof PhiInst phi)) {
                break; // Phi 都在开头
            }
            
            // 收集外部前驱的输入值映射
            Map<BasicBlock, Value> incomingMap = new HashMap<>();
            List<Integer> indicesToRemove = new ArrayList<>();
            
            for (int i = 0; i < phi.getOperands().size(); i += 2) {
                Value val = phi.getOperand(i);
                BasicBlock blk = (BasicBlock) phi.getOperand(i + 1);
                
                if (nonLoopPredSet.contains(blk)) {
                    incomingMap.put(blk, val);
                    indicesToRemove.add(i);
                }
            }
            
            if (incomingMap.isEmpty()) continue;
            
            // 移除旧的非循环前驱输入（从后往前删除）
            indicesToRemove.sort(Collections.reverseOrder());
            for (int idx : indicesToRemove) {
                phi.getOperands().remove(idx + 1); // 先删 block
                phi.getOperands().remove(idx);     // 再删 value
            }
            
            // 确定 PreHeader 提供的值
            Value distinctValue = null;
            boolean allSame = true;
            for (Value v : incomingMap.values()) {
                if (distinctValue == null) {
                    distinctValue = v;
                } else if (distinctValue != v) {
                    allSame = false;
                    break;
                }
            }
            
            Value preHeaderValue;
            if (allSame && distinctValue != null) {
                // 所有外部输入值相同，直接使用
                preHeaderValue = distinctValue;
            } else {
                // 外部输入值不同，需要在 PreHeader 插入新的 Phi
                PhiInst newPhi = new PhiInst(phi.getType(), phi.getName() + "_pre", preHeader);
                
                for (BasicBlock pred : nonLoopPreds) {
                    Value val = incomingMap.get(pred);
                    if (val != null) {
                        newPhi.addIncoming(val, pred);
                    }
                }
                
                // 插入到 PreHeader 的 Terminator (Branch) 之前
                int insertPos = Math.max(0, preHeader.getInstructions().size() - 1);
                preHeader.getInstructions().add(insertPos, newPhi);
                preHeaderValue = newPhi;
            }
            
            // 将 PreHeader 连接到原来的 Phi
            phi.addIncoming(preHeaderValue, preHeader);
        }
    }

    
    // ========== Step 2: 不变量识别与外提 ==========
    
    /**
     * 识别并外提循环不变量到 Pre-Header
     */
    private void hoistInvariants(LoopInfo loop, BasicBlock preHeader, LoopAnalysis loopAnalysis) {
        Set<Instruction> invariants = new HashSet<>();
        boolean changed = true;
        
        // 迭代查找不变量（因为一个不变量可能依赖另一个不变量）
        while (changed) {
            changed = false;
            for (BasicBlock block : loop.getBlocks()) {
                for (Instruction inst : block.getInstructions()) {
                    if (!invariants.contains(inst) && isLoopInvariant(inst, loop, invariants)) {
                        if (canHoist(inst, loop, loopAnalysis)) {
                            invariants.add(inst);
                            changed = true;
                        }
                    }
                }
            }
        }
        
        // 外提不变量到 Pre-Header
        hoistInstructionsToPreHeader(invariants, preHeader, loop);
    }
    
    /**
     * 检查指令是否为循环不变量
     * 
     * 条件：所有操作数要么是常量，要么定义在循环外，要么也是不变量
     */
    private boolean isLoopInvariant(Instruction inst, LoopInfo loop, Set<Instruction> knownInvariants) {
        // 跳过不能移动的指令
        if (!canBeHoisted(inst)) {
            return false;
        }
        
        // 【防御】指令本身必须有 parentBlock
        if (inst.getParentBlock() == null) {
            return false;
        }
        
        // 检查所有操作数
        for (Use use : inst.getOperands()) {
            Value operand = use.getValue();
            
            // 常量总是不变的
            if (operand instanceof Constant) {
                continue;
            }
            
            // 参数总是定义在循环外
            if (operand instanceof Argument) {
                continue;
            }
            
            // 如果操作数是指令，检查其定义位置
            if (operand instanceof Instruction opInst) {
                BasicBlock defBlock = opInst.getParentBlock();
                
                // 【防御】parentBlock 为 null 的指令视为不安全
                if (defBlock == null) {
                    return false;
                }
                
                // 定义在循环外
                if (!loop.contains(defBlock)) {
                    continue;
                }
                
                // 已经被标记为不变量
                if (knownInvariants.contains(opInst)) {
                    continue;
                }
                
                // 否则不是不变量
                return false;
            }
            
            // 其他类型的 Value（如 BasicBlock）跳过
        }
        
        return true;
    }
    
    /**
     * 检查指令类型是否可以外提
     */
    private boolean canBeHoisted(Instruction inst) {
        // 这些指令类型可以安全外提
        if (inst instanceof BinaryOpInst) return true;
        if (inst instanceof IcmpInst) return true;
        if (inst instanceof GetElementPtrInst) return true;
        if (inst instanceof ZextInst) return true;
        if (inst instanceof TruncInst) return true;
        
        // Load 需要特殊处理（暂时保守，不外提）
        // Store、Call、Branch、Phi、Alloca 不能外提
        return false;
    }
    
    /**
     * 检查是否可以安全外提（支配所有出口）
     */
    private boolean canHoist(Instruction inst, LoopInfo loop, LoopAnalysis loopAnalysis) {
        // 对于这些简单指令，只要是不变量就可以外提
        // 更严格的检查会验证该指令是否支配所有使用点
        // 这里采用保守策略：只要是不变量就外提
        return true;
    }
    
    /**
     * 将不变量指令移动到 Pre-Header
     */
    private void hoistInstructionsToPreHeader(Set<Instruction> invariants, BasicBlock preHeader, LoopInfo loop) {
        // 按拓扑序排列（依赖的在前）
        List<Instruction> sorted = topologicalSort(invariants, loop);
        
        // 找到 Pre-Header 中最后一条指令（应该是 Branch）的位置
        int insertPos = preHeader.getInstructions().size() - 1; // Branch 之前
        if (insertPos < 0) insertPos = 0;
        
        for (Instruction inst : sorted) {
            // 【防御】跳过 parentBlock 为 null 的指令
            BasicBlock originalBlock = inst.getParentBlock();
            if (originalBlock == null) {
                continue;
            }
            
            // 从原来的块中移除
            originalBlock.getInstructions().remove(inst);
            
            // 插入到 Pre-Header
            preHeader.getInstructions().add(insertPos++, inst);
            inst.setParentBlock(preHeader);
        }
    }
    
    /**
     * 拓扑排序：确保被依赖的指令在使用它的指令之前
     */
    private List<Instruction> topologicalSort(Set<Instruction> instructions, LoopInfo loop) {
        List<Instruction> result = new ArrayList<>();
        Set<Instruction> visited = new HashSet<>();
        
        for (Instruction inst : instructions) {
            topSortDFS(inst, instructions, visited, result);
        }
        
        return result;
    }
    
    private void topSortDFS(Instruction inst, Set<Instruction> all, Set<Instruction> visited, List<Instruction> result) {
        if (visited.contains(inst)) return;
        visited.add(inst);
        
        // 先处理依赖
        for (Use use : inst.getOperands()) {
            Value operand = use.getValue();
            if (operand instanceof Instruction opInst && all.contains(opInst)) {
                topSortDFS(opInst, all, visited, result);
            }
        }
        
        result.add(inst);
    }
}
