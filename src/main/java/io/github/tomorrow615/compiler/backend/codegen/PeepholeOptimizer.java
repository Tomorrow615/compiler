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
        
        // [Move Optimization] 优化 5: 消除计算后的冗余 move 指令
        for (MipsBasicBlock block : blocks) {
            optimizeMoves(block);
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
    
    /**
     * [Move Optimization] 消除计算后的冗余 move 指令
     * 
     * 模式匹配：
     * 1. 指令 A: 计算指令（目标寄存器为 $t0-$t9）
     * 2. 指令 B: move 指令（源 == A.dest，目标为 $a0-$a3）
     * 3. A 和 B 必须紧邻
     * 4. A.dest 在 B 之后不再被使用
     * 
     * 优化动作：
     * - 将 A 的目标寄存器修改为 B 的目标寄存器
     * - 删除 B
     */
    private void optimizeMoves(MipsBasicBlock block) {
        List<MipsInstruction> insts = block.getInstructions();
        List<MipsInstruction> toRemove = new ArrayList<>();
        
        for (int i = 0; i < insts.size() - 1; i++) {
            MipsInstruction instA = insts.get(i);
            MipsInstruction instB = insts.get(i + 1);
            
            // 检查模式
            if (!isComputeInstruction(instA)) continue;
            if (!(instB instanceof MipsMove)) continue;
            
            // 获取指令 A 的目标寄存器
            List<Operand> defsA = instA.getDef();
            if (defsA.isEmpty()) continue;
            Operand destA = defsA.get(0);
            
            // 检查 A 的目标是否为临时寄存器 $t0-$t9
            if (!isTempRegister(destA)) continue;
            
            // 获取 move 指令的 src 和 dest
            MipsMove move = (MipsMove) instB;
            List<Operand> moveDefs = move.getDef();
            List<Operand> moveUses = move.getUse();
            
            if (moveDefs.isEmpty() || moveUses.isEmpty()) continue;
            
            Operand moveSrc = moveUses.get(0);
            Operand moveDest = moveDefs.get(0);
            
            // 检查 move 的源是否等于 A 的目标
            if (!moveSrc.equals(destA)) continue;
            
            // 检查 move 的目标是否为参数寄存器 $a0-$a3
            if (!isArgRegister(moveDest)) continue;
            
            // 安全检查：确保 destA 在 move 之后不再被使用
            if (isRegisterUsedAfter(destA, i + 2, insts)) continue;
            
            // 执行优化：修改 A 的目标寄存器为 move 的目标寄存器
            instA.replaceDef(destA, moveDest);
            
            // 标记 move 指令待删除
            toRemove.add(instB);
            removedCount++;
        }
        
        // 删除标记的指令
        insts.removeAll(toRemove);
    }
    
    /**
     * 检查指令是否为计算指令（支持直接修改目标寄存器的指令）
     */
    private boolean isComputeInstruction(MipsInstruction inst) {
        if (inst instanceof MipsBinary binary) {
            String op = binary.getOp();
            // 排除无目标寄存器的指令（如 div, mult）
            if (op.equals("div") || op.equals("mult")) {
                return false;
            }
            // 排除 mflo/mfhi（虽然有目标，但通常紧跟 div/mult，不适合优化）
            if (op.equals("mflo") || op.equals("mfhi")) {
                return false;
            }
            return true;
        }
        // 可以根据需要添加其他指令类型（如 MipsLoadStore 的 lw）
        if (inst instanceof MipsLi) {
            return true;
        }
        return false;
    }
    
    /**
     * 检查操作数是否为临时寄存器 $t0-$t9
     */
    private boolean isTempRegister(Operand operand) {
        if (!(operand instanceof io.github.tomorrow615.compiler.backend.mips.MipsRegister)) {
            return false;
        }
        io.github.tomorrow615.compiler.backend.mips.MipsRegister reg = 
            (io.github.tomorrow615.compiler.backend.mips.MipsRegister) operand;
        int id = reg.getId();
        // $t0-$t7: id 8-15, $t8-$t9: id 24-25
        return (id >= 8 && id <= 15) || (id >= 24 && id <= 25);
    }
    
    /**
     * 检查操作数是否为参数寄存器 $a0-$a3
     */
    private boolean isArgRegister(Operand operand) {
        if (!(operand instanceof io.github.tomorrow615.compiler.backend.mips.MipsRegister)) {
            return false;
        }
        io.github.tomorrow615.compiler.backend.mips.MipsRegister reg = 
            (io.github.tomorrow615.compiler.backend.mips.MipsRegister) operand;
        int id = reg.getId();
        // $a0-$a3: id 4-7
        return id >= 4 && id <= 7;
    }
    
    /**
     * 检查寄存器在指定位置之后是否还被使用（活跃性检查）
     */
    private boolean isRegisterUsedAfter(Operand reg, int startIdx, List<MipsInstruction> insts) {
        for (int i = startIdx; i < insts.size(); i++) {
            MipsInstruction inst = insts.get(i);
            
            // 检查是否在 Use 列表中
            for (Operand use : inst.getUse()) {
                if (use.equals(reg)) {
                    return true;
                }
            }
            
            // 如果寄存器被重新定义（Def），则之后的使用与我们无关
            for (Operand def : inst.getDef()) {
                if (def.equals(reg)) {
                    return false;
                }
            }
        }
        return false;
    }
}
