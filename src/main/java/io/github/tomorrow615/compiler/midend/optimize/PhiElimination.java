package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.*;

/**
 * Phi 消除 Pass
 * 将 Phi 节点转换为 Move 指令，使用两阶段临时变量策略避免并行拷贝问题
 */
public class PhiElimination implements Pass {
    
    @Override
    public String getName() {
        return "PhiElimination";
    }

    @Override
    public void runOnFunction(Function func) {
        if (func.isDeclaration()) {
            return;
        }
        
        // 按基本块处理 Phi
        for (BasicBlock bb : func.getBasicBlocks()) {
            eliminatePhisInBlock(bb);
        }
    }
    
    /**
     * 处理一个基本块中的所有 Phi 节点
     */
    private void eliminatePhisInBlock(BasicBlock bb) {
        // 1. 收集该块的所有 Phi 指令
        List<PhiInst> phis = new ArrayList<>();
        for (Instruction inst : bb.getInstructions()) {
            if (inst instanceof PhiInst) {
                phis.add((PhiInst) inst);
            } else {
                break;  // Phi 都在开头
            }
        }
        
        if (phis.isEmpty()) {
            return;
        }
        
        // 2. 按前驱块分组处理
        Set<BasicBlock> predecessors = new HashSet<>(bb.getPredecessors());
        for (BasicBlock pred : predecessors) {
            insertParallelCopy(pred, bb, phis);
        }
        
        // 3. 删除所有 Phi 指令
        for (PhiInst phi : phis) {
            bb.getInstructions().remove(phi);
        }
    }
    
    /**
     * 在前驱块中插入并行拷贝
     * 使用两阶段临时变量策略避免值覆盖问题
     */
    private void insertParallelCopy(BasicBlock pred, BasicBlock succ, List<PhiInst> phis) {
        List<Instruction> moves = new ArrayList<>();
        Map<PhiInst, Value> tempMap = new HashMap<>();
        
        // === 第一阶段：将所有源值拷贝到临时变量 ===
        for (PhiInst phi : phis) {
            Value incomingValue = null;
            
            // 找到从 pred 来的值
            for (int i = 0; i < phi.getIncomingSize(); i++) {
                if (phi.getIncomingBlock(i) == pred) {
                    incomingValue = phi.getIncomingValue(i);
                    break;
                }
            }
            
            if (incomingValue != null) {
                // 创建临时变量：temp = src
                String tempName = phi.getName() + ".temp." + pred.getName();
                MoveInst tempMove = new MoveInst(phi.getType(), tempName, incomingValue, pred);
                moves.add(tempMove);
                tempMap.put(phi, tempMove);
            }
        }
        
        // === 第二阶段：从临时变量拷贝到目标 ===
        for (PhiInst phi : phis) {
            Value tempValue = tempMap.get(phi);
            if (tempValue != null) {
                // 创建最终赋值：dest = temp
                MoveInst finalMove = new MoveInst(phi.getType(), phi.getName(), tempValue, pred);
                moves.add(finalMove);
                
                // 替换 Phi 的所有使用
                phi.replaceAllUsesWith(finalMove);
            }
        }
        
        // === 第三阶段：将所有 move 插入到前驱块的终结指令之前 ===
        List<Instruction> predInsts = pred.getInstructions();
        int insertPos = predInsts.size();
        
        // 找到终结指令的位置
        for (int i = predInsts.size() - 1; i >= 0; i--) {
            Instruction inst = predInsts.get(i);
            if (inst instanceof BranchInst || inst instanceof ReturnInst) {
                insertPos = i;
                break;
            }
        }
        
        // 批量插入
        predInsts.addAll(insertPos, moves);
    }
}
