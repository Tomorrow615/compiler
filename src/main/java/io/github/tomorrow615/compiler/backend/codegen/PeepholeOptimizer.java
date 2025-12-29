package io.github.tomorrow615.compiler.backend.codegen;

import io.github.tomorrow615.compiler.backend.mips.MipsModule;
import io.github.tomorrow615.compiler.backend.mips.assembly.*;
import io.github.tomorrow615.compiler.backend.mips.operand.Operand;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsBasicBlock;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * 窥孔优化器 (Peephole Optimizer)
 * 
 * 对 MIPS 汇编进行局部模式匹配优化，删除冗余指令。
 * 应在寄存器分配之后运行。
 * 
 * 优化规则：
 * 1. 删除跳转到下一个块的无条件跳转 (j next_label)
 * 2. 删除自赋值 move (move $t0, $t0)
 * 3. 删除连续的死存储 (相同位置的连续 sw)
 * 4. 删除无效的 li 0 后跟 add (可用 move 替代)
 */
public class PeepholeOptimizer {
    private final MipsModule module;
    private int removedCount = 0;

    public PeepholeOptimizer(MipsModule module) {
        this.module = module;
    }

    /**
     * 运行窥孔优化
     * @return 删除的指令数量
     */
    public int optimize() {
        removedCount = 0;
        
        for (MipsFunction func : module.getFunctions()) {
            optimizeFunction(func);
        }
        
        return removedCount;
    }

    private void optimizeFunction(MipsFunction func) {
        List<MipsBasicBlock> blocks = func.getBlocks();
        
        // 优化 1: 删除跳转到下一个块的无条件跳转
        removeRedundantJumps(blocks);
        
        // 优化 2-4: 块内优化
        for (MipsBasicBlock block : blocks) {
            optimizeBlock(block);
        }
    }

    /**
     * 优化 1: 删除跳转到紧邻下一个块的无条件跳转
     * 
     * 例如:
     *   j main_entry
     * main_entry:
     * 
     * 这里的 j 是冗余的，可以直接 fallthrough
     */
    private void removeRedundantJumps(List<MipsBasicBlock> blocks) {
        for (int i = 0; i < blocks.size() - 1; i++) {
            MipsBasicBlock currentBlock = blocks.get(i);
            MipsBasicBlock nextBlock = blocks.get(i + 1);
            
            List<MipsInstruction> insts = currentBlock.getInstructions();
            if (insts.isEmpty()) continue;
            
            MipsInstruction lastInst = insts.get(insts.size() - 1);
            
            // 检查是否是无条件跳转到下一个块
            if (lastInst instanceof MipsBranch branch) {
                String op = branch.getOp();
                String targetLabel = branch.getLabel();
                
                // 只处理无条件跳转 j (不是 jal, jr 等)
                if ("j".equals(op) && targetLabel != null) {
                    if (targetLabel.equals(nextBlock.getLabel())) {
                        // 目标就是下一个块，删除这条跳转
                        insts.remove(insts.size() - 1);
                        removedCount++;
                    }
                }
            }
        }
    }

    /**
     * 块内窥孔优化
     */
    private void optimizeBlock(MipsBasicBlock block) {
        List<MipsInstruction> insts = block.getInstructions();
        List<MipsInstruction> newInsts = new ArrayList<>();
        
        for (int i = 0; i < insts.size(); i++) {
            MipsInstruction inst = insts.get(i);
            MipsInstruction nextInst = (i + 1 < insts.size()) ? insts.get(i + 1) : null;
            
            // 优化 2: 删除自赋值 move
            if (inst instanceof MipsMove move) {
                List<Operand> defs = move.getDef();
                List<Operand> uses = move.getUse();
                
                if (!defs.isEmpty() && !uses.isEmpty()) {
                    Operand dst = defs.get(0);
                    Operand src = uses.get(0);
                    
                    if (dst.equals(src)) {
                        // move $t0, $t0 -> 删除
                        removedCount++;
                        continue;
                    }
                }
            }
            
            // 优化 3: 删除连续的死存储 (相同位置的连续 sw)
            if (inst instanceof MipsLoadStore ls1 && ls1.getType() == MipsLoadStore.Type.SW) {
                if (nextInst instanceof MipsLoadStore ls2 && ls2.getType() == MipsLoadStore.Type.SW) {
                    // 检查是否是相同的存储位置
                    if (ls1.getBase().equals(ls2.getBase()) && ls1.getOffset() == ls2.getOffset()) {
                        // 第一条 sw 是死存储，跳过
                        removedCount++;
                        continue;
                    }
                }
            }
            
            // 优化 4: 删除 li $t0, 0 后跟 addu $t1, $t2, $t0 (可用 move 替代)
            // 这个优化比较复杂，暂时跳过
            
            newInsts.add(inst);
        }
        
        // 替换指令列表
        insts.clear();
        insts.addAll(newInsts);
    }
}
