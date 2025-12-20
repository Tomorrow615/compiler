package io.github.tomorrow615.compiler.midend.analysis;

import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.instruction.BranchInst;
import io.github.tomorrow615.compiler.midend.llvm.instruction.Instruction;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;
import io.github.tomorrow615.compiler.midend.optimize.Pass;

/**
 * CFG 构建器
 * 遍历所有基本块，根据 BranchInst 填充前驱后继关系
 */
public class CFGBuilder implements Pass {
    
    @Override
    public String getName() {
        return "CFGBuilder";
    }

    @Override
    public void runOnFunction(Function function) {
        for (BasicBlock bb : function.getBasicBlocks()) {
            if (bb.getInstructions().isEmpty()) {
                continue;
            }
            
            Instruction lastInst = bb.getInstructions().get(bb.getInstructions().size() - 1);
            if (lastInst instanceof BranchInst br) {
                if (br.isConditional()) {
                    // 条件分支: br cond, trueTarget, falseTarget
                    BasicBlock trueTarget = (BasicBlock) br.getOperand(1);
                    BasicBlock falseTarget = (BasicBlock) br.getOperand(2);
                    
                    bb.addSuccessor(trueTarget);
                    bb.addSuccessor(falseTarget);
                    trueTarget.addPredecessor(bb);
                    falseTarget.addPredecessor(bb);
                } else {
                    // 无条件分支: br target
                    BasicBlock target = (BasicBlock) br.getOperand(0);
                    
                    bb.addSuccessor(target);
                    target.addPredecessor(bb);
                }
            }
            // ReturnInst 没有后继
        }
    }
}
