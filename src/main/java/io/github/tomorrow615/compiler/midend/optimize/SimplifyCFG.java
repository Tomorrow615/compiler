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
            // 4. 【关键】简化只有一个 incoming 的 Phi 节点
            changed |= simplifySingleIncomingPhis(function);
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
            // 1. 【关键】先通知所有后继块：移除该前驱对应的 Phi incoming
            for (BasicBlock succ : bb.getSuccessors()) {
                succ.getPredecessors().remove(bb);
                cleanPhiNodes(succ, bb);
            }
            
            // 2. 【关键增强】彻底断开块中所有指令
            for (Instruction inst : bb.getInstructions()) {
                inst.removeUseFromOperands();     // 断开 Use-Def 链
                inst.setParentBlock(null);        // 显式移除父块引用，防止幽灵引用
            }
            bb.getInstructions().clear();         // 清空指令列表
            bb.getSuccessors().clear();           // 清空后继列表
            bb.getPredecessors().clear();         // 清空前驱列表
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
     * 注意：必须正确维护 Use-Def 链，使用倒序遍历避免索引偏移问题
     */
    private void cleanPhiNodes(BasicBlock bb, BasicBlock predToRemove) {
        for (Instruction inst : bb.getInstructions()) {
            if (inst instanceof PhiInst phi) {
                List<Use> operands = phi.getOperands();
                // 【关键】使用倒序遍历，安全删除，处理可能的多重引用
                for (int i = operands.size() - 2; i >= 0; i -= 2) {
                    if (phi.getOperand(i + 1) == predToRemove) {
                        // 先断开 Use-Def 链
                        operands.get(i).setValue(null);     // 断开 Value 的引用
                        operands.get(i + 1).setValue(null); // 断开 Block 的引用
                        
                        // 然后移除
                        operands.remove(i + 1);
                        operands.remove(i);
                        // 继续检查，不 break，处理可能的多重引用（虽然标准 LLVM 不应该有）
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
    
    /**
     * 【关键优化】简化只有一个 incoming 的 Phi 节点
     * 
     * 场景：内联后创建的 Phi 可能因为死分支被删除而只剩一个 incoming，
     * 或者 mergeBlocks 把 Phi 移到了块的中间位置。
     * 
     * 【修复】遍历块中的所有指令，而不是只看开头。
     */
    private boolean simplifySingleIncomingPhis(Function function) {
        boolean changed = false;
        
        for (BasicBlock bb : function.getBasicBlocks()) {
            // 收集需要处理的 Phi，避免迭代时修改
            List<PhiInst> toSimplify = new ArrayList<>();
            
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof PhiInst phi) {
                    // 检查 Phi 是否只有一个 incoming 或没有 incoming
                    if (phi.getOperands().size() <= 2) {
                        toSimplify.add(phi);
                    }
                }
            }
            
            // 处理收集到的 Phi
            for (PhiInst phi : toSimplify) {
                if (phi.getOperands().size() == 2) {
                    // 只有一个 [value, block] 对
                    Value singleValue = phi.getOperand(0);
                    
                    // 将 Phi 的所有使用替换为这个唯一的值
                    replaceAllUsesWith(phi, singleValue);
                    
                    // 断开 Phi 的操作数引用
                    phi.removeUseFromOperands();
                    
                    // 从块中移除 Phi
                    bb.getInstructions().remove(phi);
                    
                    changed = true;
                } else if (phi.getOperands().isEmpty()) {
                    // 空 Phi（没有 incoming），删除它
                    phi.removeUseFromOperands();
                    bb.getInstructions().remove(phi);
                    changed = true;
                }
            }
        }
        
        return changed;
    }
    
    private void replaceAllUsesWith(Value oldValue, Value newValue) {
        // 复制一份，避免并发修改
        List<Use> usersSnapshot = new ArrayList<>(oldValue.getUsers());
        for (Use use : usersSnapshot) {
            use.setValue(newValue);
        }
    }
}
