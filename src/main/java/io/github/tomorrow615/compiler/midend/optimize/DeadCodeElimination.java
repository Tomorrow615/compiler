package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 死代码删除优化 Pass
 * 删除没有用户且无副作用的指令
 * 
 * 删除条件：
 * 1. 指令的结果没有被任何其他指令使用 (users.isEmpty())
 * 2. 指令本身没有副作用 (不是 store/call/branch/return)
 */
public class DeadCodeElimination implements Pass {

    @Override
    public String getName() {
        return "DeadCodeElimination";
    }

    @Override
    public void runOnFunction(Function function) {
        boolean changed = true;
        
        // 迭代删除，因为删除一条指令可能使其他指令变成死代码
        while (changed) {
            changed = false;
            
            for (BasicBlock bb : function.getBasicBlocks()) {
                List<Instruction> toRemove = new ArrayList<>();
                
                for (Instruction inst : bb.getInstructions()) {
                    if (isDead(inst)) {
                        toRemove.add(inst);
                        changed = true;
                    }
                }
                
                // 删除死指令，同时清理 Use 关系
                for (Instruction inst : toRemove) {
                    removeInstruction(inst, bb);
                }
            }
        }
    }

    /**
     * 判断指令是否可以被删除
     */
    private boolean isDead(Instruction inst) {
        // 有副作用的指令不能删除
        if (hasSideEffect(inst)) {
            return false;
        }
        
        // 如果没有用户，则是死代码
        return inst.getUsers().isEmpty();
    }

    /**
     * 判断指令是否有副作用
     */
    private boolean hasSideEffect(Instruction inst) {
        return inst instanceof StoreInst
            || inst instanceof CallInst
            || inst instanceof BranchInst
            || inst instanceof ReturnInst;
    }

    /**
     * 安全删除指令：先清理 Use 关系，再从基本块移除
     */
    private void removeInstruction(Instruction inst, BasicBlock bb) {
        // 清理该指令对其操作数的 Use 关系
        for (Use use : inst.getOperands()) {
            Value operand = use.getValue();
            if (operand != null) {
                operand.removeUse(use);
            }
        }
        
        // 从基本块移除
        bb.getInstructions().remove(inst);
    }
}
