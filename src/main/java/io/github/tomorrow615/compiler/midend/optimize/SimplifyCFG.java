package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;
import java.util.*;

/**
 * 控制流图简化 Pass
 * 1. 删除不可达的基本块
 * 2. 合并线性连接的基本块 (A -> B, A只有B一个后继，B只有A一个前驱)
 * 这对于减少 JUMP/BRANCH 指令 (Cost=2) 至关重要
 */
public class SimplifyCFG implements Pass {

    @Override
    public String getName() {
        return "SimplifyCFG";
    }

    @Override
    public void runOnFunction(Function function) {
        boolean changed = true;
        while (changed) {
            changed = false;
            // 1. 删除不可达块
            changed |= removeUnreachableBlocks(function);
            // 2. 合并基本块
            changed |= mergeBlocks(function);
        }
    }

    private boolean removeUnreachableBlocks(Function function) {
        if (function.getBasicBlocks().isEmpty()) return false;
        
        Set<BasicBlock> reachable = new HashSet<>();
        Queue<BasicBlock> worklist = new LinkedList<>();
        BasicBlock entry = function.getBasicBlocks().get(0);
        
        reachable.add(entry);
        worklist.add(entry);
        
        while (!worklist.isEmpty()) {
            BasicBlock bb = worklist.poll();
            for (BasicBlock succ : bb.getSuccessors()) {
                if (!reachable.contains(succ)) {
                    reachable.add(succ);
                    worklist.add(succ);
                }
            }
        }
        
        List<BasicBlock> toRemove = new ArrayList<>();
        for (BasicBlock bb : function.getBasicBlocks()) {
            if (!reachable.contains(bb)) {
                toRemove.add(bb);
            }
        }
        
        if (toRemove.isEmpty()) return false;
        
        // 清理前驱后继关系
        for (BasicBlock bb : toRemove) {
            for (BasicBlock succ : bb.getSuccessors()) {
                succ.getPredecessors().remove(bb);
                // 如果后继有 Phi 节点，需要移除对应的 incoming
                cleanPhiNodes(succ, bb);
            }
        }
        function.getBasicBlocks().removeAll(toRemove);
        return true;
    }

    private boolean mergeBlocks(Function function) {
        boolean changed = false;
        // 使用新列表避免迭代时修改
        List<BasicBlock> blocks = new ArrayList<>(function.getBasicBlocks());
        
        for (BasicBlock bb : blocks) {
            // 跳过已删除的块
            if (!function.getBasicBlocks().contains(bb)) continue;
            
            // 检查是否可以合并后继
            // 条件：bb 有且仅有一个后继 succ，且 succ 有且仅有一个前驱 bb
            // 注意：succ 不能是 entry (虽然一般不会发生)
            if (bb.getSuccessors().size() == 1) {
                BasicBlock succ = bb.getSuccessors().get(0);
                if (succ != function.getBasicBlocks().get(0) && 
                    succ.getPredecessors().size() == 1 && 
                    succ.getPredecessors().get(0) == bb) {
                    doMerge(bb, succ, function);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private void doMerge(BasicBlock bb, BasicBlock succ, Function function) {
        // 1. 移除 bb 末尾的跳转指令
        Instruction terminator = bb.getInstructions().get(bb.getInstructions().size() - 1);
        terminator.removeUseFromOperands(); // 断开对 succ 的引用
        bb.getInstructions().remove(bb.getInstructions().size() - 1);
        
        // 2. 将 succ 的所有指令移动到 bb 末尾
        for (Instruction inst : succ.getInstructions()) {
            inst.setParentBlock(bb);
            bb.getInstructions().add(inst);
        }
        
        // 3. 更新 succ 的后继的前驱指向
        // 原来是 succ -> next，现在变成 bb -> next
        for (BasicBlock next : succ.getSuccessors()) {
            // 更新 CFG 链表
            next.getPredecessors().remove(succ);
            next.getPredecessors().add(bb);
            // 更新 Phi 节点中的引用
            replacePhiPredecessor(next, succ, bb);
        }
        
        // 4. 更新 bb 的后继列表
        bb.getSuccessors().clear();
        bb.getSuccessors().addAll(succ.getSuccessors());
        
        // 5. 从函数中移除 succ
        function.getBasicBlocks().remove(succ);
    }
    
    private void cleanPhiNodes(BasicBlock bb, BasicBlock predToRemove) {
        for (Instruction inst : bb.getInstructions()) {
            if (inst instanceof PhiInst phi) {
                // 找到 predToRemove 对应的操作数索引
                for (int i = 0; i < phi.getOperands().size(); i += 2) {
                    if (phi.getOperand(i + 1) == predToRemove) {
                        phi.getOperands().remove(i + 1); // 移除 Block
                        phi.getOperands().remove(i);     // 移除 Value
                        break;
                    }
                }
            } else {
                break; // Phi 都在开头
            }
        }
    }

    private void replacePhiPredecessor(BasicBlock bb, BasicBlock oldPred, BasicBlock newPred) {
        for (Instruction inst : bb.getInstructions()) {
            if (inst instanceof PhiInst phi) {
                for (int i = 0; i < phi.getOperands().size(); i += 2) {
                    if (phi.getOperand(i + 1) == oldPred) {
                        phi.setOperand(i + 1, newPred);
                    }
                }
            } else {
                break;
            }
        }
    }
}
