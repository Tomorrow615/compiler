package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;
import java.util.*;

/**
 * 控制流图简化 Pass (增强版)
 * 1. 常量分支折叠 (br i1 true -> br label)
 * 2. 删除不可达的基本块
 * 3. 合并线性连接的基本块
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
            // 1. 常量分支折叠 (必须在前，切断不可达块的边)
            changed |= foldBranchToJump(function);
            // 2. 删除不可达块
            changed |= removeUnreachableBlocks(function);
            // 3. 合并基本块
            changed |= mergeBlocks(function);
        }
    }

    /**
     * 常量分支折叠：将条件为常量的 Branch 转化为无条件跳转
     */
    private boolean foldBranchToJump(Function function) {
        boolean changed = false;
        for (BasicBlock bb : function.getBasicBlocks()) {
            if (bb.getInstructions().isEmpty()) continue;
            Instruction terminator = bb.getInstructions().get(bb.getInstructions().size() - 1);

            if (terminator instanceof BranchInst br && br.isConditional()) {
                Value cond = br.getCondition();
                if (cond instanceof ConstantInt constCond) {
                    // 确定跳转的目标
                    BasicBlock dest = constCond.getValue() != 0 ? br.getTrueBlock() : br.getFalseBlock();
                    BasicBlock deadDest = constCond.getValue() != 0 ? br.getFalseBlock() : br.getTrueBlock();

                    // 【特殊情况处理】如果 dest 和 deadDest 是同一个块 (br i1 true, label %A, label %A)
                    // 这种情况 Phi 节点处理比较复杂，且收益不高，跳过
                    if (dest == deadDest) continue;

                    // 1. 清理被放弃分支的 Phi 节点
                    cleanPhiNodes(deadDest, bb);

                    // 2. 【关键修复】维护 CFG：从 deadDest 的前驱列表中移除当前块 bb
                    deadDest.getPredecessors().remove(bb);

                    // 3. 创建新的无条件跳转
                    BranchInst newBr = new BranchInst(dest, null);
                    newBr.setParentBlock(bb);
                    
                    // 4. 移除旧指令引用并替换
                    br.removeUseFromOperands();
                    bb.getInstructions().set(bb.getInstructions().size() - 1, newBr);
                    
                    // 5. 更新当前块的后继列表
                    bb.getSuccessors().clear();
                    bb.addSuccessor(dest);
                    
                    changed = true;
                }
            }
        }
        return changed;
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
        
        for (BasicBlock bb : toRemove) {
            for (BasicBlock succ : bb.getSuccessors()) {
                succ.getPredecessors().remove(bb);
                cleanPhiNodes(succ, bb);
            }
        }
        function.getBasicBlocks().removeAll(toRemove);
        return true;
    }

    private boolean mergeBlocks(Function function) {
        boolean changed = false;
        List<BasicBlock> blocks = new ArrayList<>(function.getBasicBlocks());
        
        for (BasicBlock bb : blocks) {
            if (!function.getBasicBlocks().contains(bb)) continue;
            
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
        Instruction terminator = bb.getInstructions().get(bb.getInstructions().size() - 1);
        terminator.removeUseFromOperands();
        bb.getInstructions().remove(bb.getInstructions().size() - 1);
        
        for (Instruction inst : succ.getInstructions()) {
            inst.setParentBlock(bb);
            bb.getInstructions().add(inst);
        }
        
        for (BasicBlock next : succ.getSuccessors()) {
            next.getPredecessors().remove(succ);
            next.getPredecessors().add(bb);
            replacePhiPredecessor(next, succ, bb);
        }
        
        bb.getSuccessors().clear();
        bb.getSuccessors().addAll(succ.getSuccessors());
        function.getBasicBlocks().remove(succ);
    }
    
    /**
     * 【修复】清理 Phi 节点，移除指定前驱的 incoming
     */
    private void cleanPhiNodes(BasicBlock bb, BasicBlock predToRemove) {
        for (Instruction inst : bb.getInstructions()) {
            if (inst instanceof PhiInst phi) {
                for (int i = 0; i < phi.getOperands().size(); i += 2) {
                    if (phi.getOperand(i + 1) == predToRemove) {
                        phi.getOperands().remove(i + 1); // 移除 Block
                        phi.getOperands().remove(i);     // 移除 Value
                        // 标准 LLVM 中一个前驱在 Phi 中只出现一次
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
