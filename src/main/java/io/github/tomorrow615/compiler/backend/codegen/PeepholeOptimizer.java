package io.github.tomorrow615.compiler.backend.codegen;

import io.github.tomorrow615.compiler.backend.mips.MipsModule;
import io.github.tomorrow615.compiler.backend.mips.MipsRegister;
import io.github.tomorrow615.compiler.backend.mips.assembly.*;
import io.github.tomorrow615.compiler.backend.mips.operand.Operand;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsBasicBlock;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;
import io.github.tomorrow615.compiler.util.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
 * 4. SW+LW 转发 (紧邻的 sw/lw 同一地址 → sw + move)
 * 5. LW+LW 冗余消除 (紧邻的 lw 同一地址 → lw + move)
 * 6. Move 合并 (calc $t + move $a → calc $a)
 * 
 * 使用迭代收敛机制，多轮执行直到无优化机会或达到最大迭代次数。
 */
public class PeepholeOptimizer {
    private final MipsModule module;
    private int removedCount = 0;

    /**
     * 最大迭代次数，防止死循环
     */
    private static final int MAX_ITERATIONS = 15;

    public PeepholeOptimizer(MipsModule module) {
        this.module = module;
    }

    /**
     * 运行窥孔优化（迭代收敛）
     * <p>
     * 迭代执行直到不再有优化机会，或达到最大迭代次数。
     * 这样可以发现连锁优化机会（如删除 move 后可能产生新的自赋值）。
     *
     * @return 删除的指令总数
     */
    public int optimize() {
        int totalRemoved = 0;
        boolean changed;
        int iteration = 0;

        do {
            removedCount = 0;

            for (MipsFunction func : module.getFunctions()) {
                optimizeFunction(func);
            }

            totalRemoved += removedCount;
            changed = (removedCount > 0);
            iteration++;
        } while (changed && iteration < MAX_ITERATIONS);

        return totalRemoved;
    }

    private void optimizeFunction(MipsFunction func) {
        List<MipsBasicBlock> blocks = func.getBlocks();

        // 优化 1: 删除跳转到下一个块的无条件跳转
        removeRedundantJumps(blocks);

        // 优化 2-5: 块内优化（紧邻模式）
        for (MipsBasicBlock block : blocks) {
            optimizeBlock(block);
        }

        // [激进优化] P0-P1: 深度优化
        if (Config.ENABLE_AGGRESSIVE_PEEPHOLE) {
            // P1: 分支与跳转增强 (涉及跨块分析)
            optimizeBranches(blocks);

            for (MipsBasicBlock block : blocks) {
                // P0: 跨指令 Store-Load 转发
                deepMemoryOptimize(block);
                // P1: 栈指针合并
                mergeSpAdjustments(block);
                // P2: Move 链消除
                optimizeMoveChains(block);
                // P2: 逻辑指令优化
                optimizeLogicSequence(block);

                // [Legacy Merged] P3: 全局栈访问优化 (增强版 optimizeMemoryAccess)
                optimizeGlobalStackAccess(block);

                // [Legacy Merged] P3: 零运算与恒等优化
                optimizeZeroOps(block);

                // [Extreme] P4: 极端激进优化
                if (Config.ENABLE_EXTREME_PEEPHOLE) {
                    optimizeRedundantDefs(block);
                    deepDeadStoreElimination(block);
                    optimizeStrengthReduction(block);
                    
                    // [P5] 更激进的优化
                    optimizeLiCombine(block);
                    optimizeDoubleMoves(block);
                    // optimizeAggressiveLoadElimination(block); // 暂时禁用：别名假设太激进
                }
            }
        }

        // [Move Optimization] 优化 6: 消除计算后的冗余 move 指令
        for (MipsBasicBlock block : blocks) {
            optimizeMoves(block);
        }
        
        // [Aggressive] 优化 7: 增强内存优化（跨指令 Store-Load 跟踪，仅限栈地址）
        if (Config.ENABLE_AGGRESSIVE_PEEPHOLE) {
            for (MipsBasicBlock block : blocks) {
                optimizeMemoryAccess(block);
            }
        }
    }

    /**
     * 优化 1: 删除跳转到紧邻下一个块的无条件跳转
     * <p>
     * 例如:
     * j main_entry
     * main_entry:
     * <p>
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

            // 优化 3/4/5: 内存访问优化（紧邻的 Load/Store 同一地址）
            if (inst instanceof MipsLoadStore ls1 && nextInst instanceof MipsLoadStore ls2) {
                // 必须是同一地址：base 相同 && offset 相同
                if (isSameMemoryLocation(ls1, ls2)) {
                    // Case A: SW + SW (死存储)
                    if (ls1.getType() == MipsLoadStore.Type.SW && ls2.getType() == MipsLoadStore.Type.SW) {
                        // 第一条 sw 是死存储，跳过
                        removedCount++;
                        continue;
                    }

                    // Case B: SW + LW (Store-Load 转发) - 安全！
                    // sw $t0, 4($sp) + lw $t1, 4($sp)
                    // 优化为: sw $t0, 4($sp) + move $t1, $t0 (或删除 lw 如果同寄存器)
                    // 安全原因: SW 保留，$t0 的定义不受影响
                    if (ls1.getType() == MipsLoadStore.Type.SW && ls2.getType() == MipsLoadStore.Type.LW) {
                        Operand storedReg = ls1.getRt();  // SW 的数据源
                        Operand loadDest = ls2.getRt();   // LW 的目标

                        // 添加当前指令到结果列表
                        newInsts.add(inst);

                        if (storedReg.equals(loadDest)) {
                            // sw $t0, ... + lw $t0, ... -> 删除 lw (数据已在寄存器中)
                            removedCount++;
                            i++; // 跳过 nextInst
                            continue;
                        } else {
                            // sw $t0, ... + lw $t1, ... -> sw $t0, ... + move $t1, $t0
                            newInsts.add(new MipsMove(loadDest, storedReg));
                            removedCount++;
                            i++; // 跳过原 nextInst
                            continue;
                        }
                    }

                    // Case C: LW + LW (冗余加载) - 安全！
                    // lw $t0, 4($sp) + lw $t1, 4($sp)
                    // 优化为: lw $t0, 4($sp) + move $t1, $t0 (或删除第二个 lw 如果同寄存器)
                    // 安全原因: 第一个 LW 保留
                    if (ls1.getType() == MipsLoadStore.Type.LW && ls2.getType() == MipsLoadStore.Type.LW) {
                        Operand loadDest1 = ls1.getRt();
                        Operand loadDest2 = ls2.getRt();

                        // 添加当前指令到结果列表
                        newInsts.add(inst);

                        if (loadDest1.equals(loadDest2)) {
                            // lw $t0, ... + lw $t0, ... -> 删除第二个 lw (冗余)
                            removedCount++;
                            i++; // 跳过 nextInst
                            continue;
                        } else {
                            // lw $t0, ... + lw $t1, ... -> lw $t0, ... + move $t1, $t0
                            newInsts.add(new MipsMove(loadDest2, loadDest1));
                            removedCount++;
                            i++; // 跳过原 nextInst
                            continue;
                        }
                    }
                }
            }

            newInsts.add(inst);
        }

        // 替换指令列表
        insts.clear();
        insts.addAll(newInsts);
    }

    /**
     * 检查两条 LoadStore 指令是否访问相同的内存地址
     */
    private boolean isSameMemoryLocation(MipsLoadStore ls1, MipsLoadStore ls2) {
        return ls1.getBase().equals(ls2.getBase()) && ls1.getOffset() == ls2.getOffset();
    }

    /**
     * [Move Optimization] 消除计算后的冗余 move 指令
     * <p>
     * 模式匹配：
     * 1. 指令 A: 计算指令（有目标寄存器）
     * 2. 指令 B: move 指令（源 == A.dest）
     * 3. A 和 B 必须紧邻
     * 4. A.dest 和 B.dest 都不是特殊寄存器（$zero, $sp, $ra, $gp）
     * 5. A.dest 在 B 之后不再被使用
     * <p>
     * 优化动作：
     * - 将 A 的目标寄存器修改为 B 的目标寄存器
     * - 删除 B
     * <p>
     * 改进：放宽了寄存器类型限制，现在支持任意非特殊寄存器间的 move 合并
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

            // 安全检查1: A 的目标寄存器不能是特殊寄存器
            if (isSpecialRegister(destA)) continue;

            // 获取 move 指令的 src 和 dest
            MipsMove move = (MipsMove) instB;
            List<Operand> moveDefs = move.getDef();
            List<Operand> moveUses = move.getUse();

            if (moveDefs.isEmpty() || moveUses.isEmpty()) continue;

            Operand moveSrc = moveUses.get(0);
            Operand moveDest = moveDefs.get(0);

            // 检查 move 的源是否等于 A 的目标
            if (!moveSrc.equals(destA)) continue;

            // 安全检查2: move 的目标寄存器也不能是特殊寄存器
            if (isSpecialRegister(moveDest)) continue;

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
     * 检查操作数是否为特殊寄存器（不能参与优化的寄存器）
     * 特殊寄存器包括：$zero, $gp, $sp, $ra
     */
    private boolean isSpecialRegister(Operand operand) {
        if (!(operand instanceof io.github.tomorrow615.compiler.backend.mips.MipsRegister)) {
            return false;
        }
        io.github.tomorrow615.compiler.backend.mips.MipsRegister reg =
                (io.github.tomorrow615.compiler.backend.mips.MipsRegister) operand;
        int id = reg.getId();
        // $zero=0, $gp=28, $sp=29, $ra=31
        return id == 0 || id == 28 || id == 29 || id == 31;
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

    // ==================== 激进窥孔优化 (Config.ENABLE_AGGRESSIVE_PEEPHOLE) ====================

    /**
     * 跨指令扫描的最大深度 (Extreme: 增大到 16)
     */
    private static final int MAX_LOOKAHEAD = 32;

    /**
     * [P0] 跨指令 Store-Load 转发
     * <p>
     * 向后扫描最多 MAX_LOOKAHEAD 条指令，找到同地址的 LW 进行转发。
     * <p>
     * 模式：
     * sw $t0, 4($sp)
     * addu $t2, $t3, $t4  # 不相关指令
     * lw $t1, 4($sp)      # 可转发为 move $t1, $t0
     * <p>
     * 安全终止条件：
     * - 遇到分支/跳转指令
     * - base 寄存器被重定义
     * - 源寄存器被重定义
     * - 同地址有新的 SW（覆盖）
     */
    private void deepMemoryOptimize(MipsBasicBlock block) {
        List<MipsInstruction> insts = block.getInstructions();
        Set<Integer> toRemove = new HashSet<>();

        for (int i = 0; i < insts.size(); i++) {
            if (toRemove.contains(i)) continue;
            MipsInstruction cur = insts.get(i);

            // 只处理 SW 指令
            if (!(cur instanceof MipsLoadStore sw) || sw.getType() != MipsLoadStore.Type.SW) {
                continue;
            }

            Operand storedReg = sw.getRt();  // SW 存储的寄存器
            Operand base = sw.getBase();      // 基址寄存器
            int offset = sw.getOffset();      // 偏移量

            // 向后扫描最多 MAX_LOOKAHEAD 条指令
            for (int j = i + 1; j < Math.min(i + MAX_LOOKAHEAD, insts.size()); j++) {
                if (toRemove.contains(j)) continue;
                MipsInstruction future = insts.get(j);

                // 终止条件 1: 遇到分支/跳转
                if (future instanceof MipsBranch) break;

                // 终止条件 2: base 寄存器被重定义
                if (definesRegister(future, base)) break;

                // 终止条件 3: 源寄存器被重定义
                if (definesRegister(future, storedReg)) break;

                // 终止条件 4: 同地址有新的 SW（覆盖）
                if (future instanceof MipsLoadStore futureLs
                        && futureLs.getType() == MipsLoadStore.Type.SW
                        && futureLs.getBase().equals(base)
                        && futureLs.getOffset() == offset) {
                    break;
                }

                // 匹配：同地址的 LW
                if (future instanceof MipsLoadStore lw
                        && lw.getType() == MipsLoadStore.Type.LW
                        && lw.getBase().equals(base)
                        && lw.getOffset() == offset) {

                    Operand loadDest = lw.getRt();

                    if (loadDest.equals(storedReg)) {
                        // sw $t0, ... + lw $t0, ... → 删除 lw
                        toRemove.add(j);
                    } else {
                        // sw $t0, ... + lw $t1, ... → 替换为 move $t1, $t0
                        insts.set(j, new MipsMove(loadDest, storedReg));
                    }
                    removedCount++;
                    break;  // 只优化第一个匹配的 LW
                }
            }
        }

        // 删除标记的指令
        if (!toRemove.isEmpty()) {
            removeByIndices(insts, toRemove);
        }
    }

    /**
     * 检查指令是否定义（写入）指定的寄存器
     */
    private boolean definesRegister(MipsInstruction inst, Operand reg) {
        for (Operand def : inst.getDef()) {
            if (def.equals(reg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按索引集合删除指令
     */
    private void removeByIndices(List<MipsInstruction> insts, Set<Integer> indices) {
        List<MipsInstruction> newInsts = new ArrayList<>();
        for (int i = 0; i < insts.size(); i++) {
            if (!indices.contains(i)) {
                newInsts.add(insts.get(i));
            }
        }
        insts.clear();
        insts.addAll(newInsts);
    }

    /**
     * [P1] 分支与跳转增强
     * 1. 删除条件跳转到下一块的指令
     * 2. 分支-跳转合并：beq L1 + j L2 + L1: → bne L2
     */
    private void optimizeBranches(List<MipsBasicBlock> blocks) {
        for (int i = 0; i < blocks.size() - 1; i++) {
            MipsBasicBlock curr = blocks.get(i);
            MipsBasicBlock next = blocks.get(i + 1);
            List<MipsInstruction> insts = curr.getInstructions();

            if (insts.isEmpty()) continue;
            MipsInstruction last = insts.get(insts.size() - 1);

            // Case 1: 条件分支跳转到下一块 → 删除
            // beq $t0, $t1, L_NEXT
            // L_NEXT:
            if (last instanceof MipsBranch br && isConditionalBranch(br.getOp())) {
                if (br.getLabel() != null && br.getLabel().equals(next.getLabel())) {
                    insts.remove(insts.size() - 1);
                    removedCount++;
                    continue; // 重新检查新的结尾
                }
            }

            // Case 2: beq L1 + j L2 + L1: → bne L2
            if (insts.size() >= 2) {
                MipsInstruction secondLast = insts.get(insts.size() - 2);
                last = insts.get(insts.size() - 1); // update last

                if (secondLast instanceof MipsBranch cond
                        && isConditionalBranch(cond.getOp())
                        && last instanceof MipsBranch jump
                        && "j".equals(jump.getOp())) {

                    // 检查条件跳转的目标是否就是下一块 (即: L1)
                    if (cond.getLabel() != null && cond.getLabel().equals(next.getLabel())) {
                        String invertedOp = invertCondition(cond.getOp());
                        if (invertedOp != null) {
                            // 构造反向分支跳转到 j 的目标 (L2)
                            MipsBranch newBranch = createBranch(invertedOp, cond.getRs(), cond.getRt(), jump.getLabel());

                            // 替换 cond，删除 jump
                            insts.set(insts.size() - 2, newBranch);
                            insts.remove(insts.size() - 1);
                            removedCount++;
                        }
                    }
                }
            }
        }
    }

    /**
     * [P1] 栈指针操作合并
     * 合并连续的 addiu $sp, $sp, imm
     */
    private void mergeSpAdjustments(MipsBasicBlock block) {
        List<MipsInstruction> insts = block.getInstructions();
        List<MipsInstruction> newInsts = new ArrayList<>();

        int pendingOffset = 0;
        boolean hasPending = false;

        for (MipsInstruction inst : insts) {
            if (isSpAdjustment(inst)) {
                pendingOffset += getSpOffset(inst);
                hasPending = true;
                removedCount++;
            } else {
                if (hasPending) {
                    if (pendingOffset != 0) {
                        newInsts.add(createSpAdjust(pendingOffset));
                    }
                    pendingOffset = 0;
                    hasPending = false;
                }
                newInsts.add(inst);
            }
        }

        // 处理末尾可能的 pending
        if (hasPending && pendingOffset != 0) {
            newInsts.add(createSpAdjust(pendingOffset));
        }

        insts.clear();
        insts.addAll(newInsts);
    }

    // ========== 辅助方法 ==========

    private boolean isConditionalBranch(String op) {
        return op.equals("beq") || op.equals("bne") ||
                op.equals("blt") || op.equals("bge") ||
                op.equals("bgt") || op.equals("ble") ||
                op.equals("beqz") || op.equals("bnez");
    }

    private String invertCondition(String op) {
        return switch (op) {
            case "beq" -> "bne";
            case "bne" -> "beq";
            case "blt" -> "bge";
            case "bge" -> "blt";
            case "bgt" -> "ble";
            case "ble" -> "bgt";
            case "beqz" -> "bnez";
            case "bnez" -> "beqz";
            default -> null;
        };
    }

    private MipsBranch createBranch(String op, Operand rs, Operand rt, String label) {
        // 根据操作数数量调用不同的构造函数
        if (rs != null && rt != null) {
            return new MipsBranch(op, rs, rt, label);
        } else if (rs != null) {
            // bnez/beqz
            return new MipsBranch(op, rs, label);
        } else {
            // j (不应该走到这里)
            return new MipsBranch(op, label);
        }
    }

    private boolean isSpAdjustment(MipsInstruction inst) {
        if (!(inst instanceof MipsBinary bin)) return false;
        String op = bin.getOp();
        if (!op.equals("addiu") && !op.equals("addi")) return false;

        // 检查是否是 $sp, $sp, imm
        // 注意：MipsBinary 的 rd 对应汇编的第一个操作数 (目标)
        Operand dest = bin.getRd();
        Operand src = bin.getRs();

        if (dest instanceof io.github.tomorrow615.compiler.backend.mips.MipsRegister regDest &&
                src instanceof io.github.tomorrow615.compiler.backend.mips.MipsRegister regSrc) {
            return regDest.getId() == 29 && regSrc.getId() == 29 && bin.getImm() != null;
        }
        return false;
    }

    private int getSpOffset(MipsInstruction inst) {
        return ((MipsBinary) inst).getImm();
    }

    private MipsInstruction createSpAdjust(int offset) {
        return new MipsBinary("addiu",
                io.github.tomorrow615.compiler.backend.mips.MipsRegister.SP,
                io.github.tomorrow615.compiler.backend.mips.MipsRegister.SP,
                offset);
    }

    /**
     * [P2] Move 链消除
     * move $t0, $s0
     * move $t1, $t0  (且 $t0 死)
     * -> move $t1, $s0
     */
    private void optimizeMoveChains(MipsBasicBlock block) {
        List<MipsInstruction> insts = block.getInstructions();
        Map<Operand, Operand> moveChain = new HashMap<>(); // dest -> src

        for (int i = 0; i < insts.size(); i++) {
            MipsInstruction inst = insts.get(i);

            if (inst instanceof MipsMove mv) {
                List<Operand> defs = mv.getDef();
                List<Operand> uses = mv.getUse();

                if (!defs.isEmpty() && !uses.isEmpty()) {
                    Operand dest = defs.get(0);
                    Operand src = uses.get(0);

                    // 检查 src 是否是 move 链的一部分
                    Operand originalSrc = src;
                    while (moveChain.containsKey(originalSrc)) {
                        originalSrc = moveChain.get(originalSrc);
                    }

                    if (!originalSrc.equals(src)) {
                        // 检查中间寄存器 src 在这里之后是否不再被使用
                        if (!isRegisterUsedAfter(src, i + 1, insts)) {
                            // 替换为直接 move
                            insts.set(i, new MipsMove(dest, originalSrc));
                            removedCount++;
                            // 更新 src 为新的源
                            src = originalSrc;
                        }
                    }

                    // 记录 move 关系 (dest 来自 src)
                    // 如果 dest 或 src 被重定义，已经在下面的 else 分支处理了
                    // 但这里我们需要先清理旧的，再添加新的
                    final Operand d = dest;
                    moveChain.entrySet().removeIf(e -> e.getKey().equals(d) || e.getValue().equals(d));

                    if (!dest.equals(src)) {
                        moveChain.put(dest, src);
                    }
                }
            } else {
                // 任何定义寄存器的指令，都会破坏相关链条
                for (Operand def : inst.getDef()) {
                    final Operand definedReg = def;
                    moveChain.entrySet().removeIf(entry ->
                            entry.getKey().equals(definedReg) || entry.getValue().equals(definedReg));
                }
            }
        }
    }

    /**
     * [P2] 逻辑指令序列优化
     * slt $t0, $s1, $s0
     * ori $at, $zero, 1
     * subu $t0, $at, $t0
     * -> slt + xori $t0, $t0, 1
     */
    private void optimizeLogicSequence(MipsBasicBlock block) {
        List<MipsInstruction> insts = block.getInstructions();
        List<Integer> toRemove = new ArrayList<>();

        for (int i = 0; i < insts.size() - 2; i++) {
            // 模式匹配
            if (matchsLogicPattern(insts, i)) {
                MipsBinary slt = (MipsBinary) insts.get(i);
                Operand dest = slt.getRd(); // slt 是 R-Type

                // 替换 ori 为 xori dest, dest, 1
                insts.set(i + 1, new MipsBinary("xori", dest, dest, 1));

                // 标记删除 subu (i+2)
                toRemove.add(i + 2);
                removedCount++;
                i += 2; // 跳过处理过的指令
            }
        }

        // 倒序删除
        for (int k = toRemove.size() - 1; k >= 0; k--) {
            insts.remove((int) toRemove.get(k));
        }
    }

    private boolean matchsLogicPattern(List<MipsInstruction> insts, int i) {
        MipsInstruction i1 = insts.get(i);
        MipsInstruction i2 = insts.get(i + 1);
        MipsInstruction i3 = insts.get(i + 2);

        if (!(i1 instanceof MipsBinary slt) || !slt.getOp().equals("slt")) return false;
        if (!(i2 instanceof MipsBinary ori) || !ori.getOp().equals("ori")) return false;
        if (!(i3 instanceof MipsBinary subu) || !subu.getOp().equals("subu")) return false;

        Operand dest = slt.getRd();
        if (dest == null) return false;

        // check ori $at, $zero, 1
        if (!ori.getRd().toString().equals("$at")) return false;
        if (!ori.getRs().toString().equals("$zero")) return false;
        if (ori.getImm() == null || ori.getImm() != 1) return false;

        // check subu dest, $at, dest
        if (!subu.getRd().equals(dest)) return false;
        if (!subu.getRs().toString().equals("$at")) return false;
        if (!subu.getRt().equals(dest)) return false;

        return true;

    }

    // ==================== [Legacy Merged] P3: 额外激进优化 ====================

    /**
     * [P3] 优化零运算与恒等操作
     * 1. li $t0, 0 -> move $t0, $zero
     * 2. addu $t0, $t1, $zero -> move $t0, $t1
     * 3. addiu $t0, $t1, 0 -> move $t0, $t1
     */
    private void optimizeZeroOps(MipsBasicBlock block) {
        List<MipsInstruction> insts = block.getInstructions();

        for (int i = 0; i < insts.size(); i++) {
            MipsInstruction inst = insts.get(i);

            // 1. li $t0, 0 -> move $t0, $zero
            if (inst instanceof MipsLi li && li.getImm() == 0) {
                insts.set(i, new MipsMove(li.getRd(), io.github.tomorrow615.compiler.backend.mips.MipsRegister.ZERO));
                removedCount++;
                continue;
            }

            if (inst instanceof MipsBinary bin) {
                String op = bin.getOp();
                Operand rd = bin.getRd();
                Operand rs = bin.getRs();
                Operand rt = bin.getRt();
                Integer imm = bin.getImm();

                // 2. addu $t0, $t1, $zero -> move $t0, $t1
                //    or $t0, $t1, $zero   -> move $t0, $t1
                //    xor $t0, $t1, $zero  -> move $t0, $t1
                if ((op.equals("addu") || op.equals("add") || op.equals("or") || op.equals("xor") || op.equals("subu"))
                        && rt != null && rt.equals(io.github.tomorrow615.compiler.backend.mips.MipsRegister.ZERO)) {
                    insts.set(i, new MipsMove(rd, rs));
                    removedCount++;
                    continue;
                }

                // 3. addiu $t0, $t1, 0 -> move $t0, $t1
                if ((op.equals("addiu") || op.equals("addi")) && imm != null && imm == 0) {
                    insts.set(i, new MipsMove(rd, rs));
                    removedCount++;
                    continue;
                }
                
                // 4. sll/srl/sra $t0, $t1, 0 -> move $t0, $t1
                if ((op.equals("sll") || op.equals("srl") || op.equals("sra")) && imm != null && imm == 0) {
                    insts.set(i, new MipsMove(rd, rs));
                    removedCount++;
                    continue;
                }
            }
        }
    }

    /**
     * 内存位置抽象
     */
    private static class MemoryLocation {
        final Operand base;
        final int offset;

        MemoryLocation(Operand base, int offset) {
            this.base = base;
            this.offset = offset;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MemoryLocation)) return false;
            MemoryLocation that = (MemoryLocation) o;
            return offset == that.offset && Objects.equals(base, that.base);
        }

        @Override
        public int hashCode() {
            return Objects.hash(base, offset);
        }
    }

    /**
     * [P3] 全局栈访问优化 (增强版 optimizeMemoryAccess)
     * <p>
     * 基于旧版本的 optimizeMemoryAccess，但增加了严格的安全性限制：
     * 1. 仅跟踪栈地址 ($sp, $fp)
     * 2. 遇到任何非栈 Store，清空所有状态 (保守策略)
     * 3. 遇到 Call/Syscall，清空所有状态
     */
    private void optimizeGlobalStackAccess(MipsBasicBlock block) {
        List<MipsInstruction> insts = block.getInstructions();
        Map<MemoryLocation, Operand> storeMap = new HashMap<>();
        List<Integer> toRemove = new ArrayList<>();

        for (int i = 0; i < insts.size(); i++) {
            MipsInstruction inst = insts.get(i);

            // 1. 函数调用/Syscall：清空状态
            if (inst instanceof MipsBranch br && (br.getOp().equals("jal") || br.getOp().equals("jalr"))) {
                storeMap.clear();
                continue;
            }
            if (inst instanceof MipsSyscall) {
                storeMap.clear();
                continue;
            }

            // 2. 处理 Load/Store
            if (inst instanceof MipsLoadStore ls) {
                Operand base = ls.getBase();
                boolean isStackAddr = false;
                if (base instanceof io.github.tomorrow615.compiler.backend.mips.MipsRegister reg) {
                    isStackAddr = (reg.getId() == 29 || reg.getId() == 30); // $sp=29, $fp=30
                }

                if (ls.getType() == MipsLoadStore.Type.SW) {
                    if (isStackAddr) {
                        // 记录栈存储
                        storeMap.put(new MemoryLocation(base, ls.getOffset()), ls.getRt());
                    } else {
                        // 非栈存储
                        // [Safety Check] 默认情况下，非栈存储可能修改任何内存（若栈地址泄露）
                        // [Extreme] 激进模式下，假设栈地址不逃逸，非栈 Store 不会影响栈变量
                        if (!Config.ENABLE_EXTREME_PEEPHOLE) {
                            storeMap.clear();
                        }
                    }
                } else if (ls.getType() == MipsLoadStore.Type.LW) {
                    Operand loadDest = ls.getRt();

                    if (isStackAddr) {
                        MemoryLocation loc = new MemoryLocation(base, ls.getOffset());
                        Operand storedReg = storeMap.get(loc);

                        if (storedReg != null) {
                            if (storedReg.equals(loadDest)) {
                                // sw $t0, addr ... lw $t0, addr -> 删除 lw
                                // 注意：这里不能直接 remove，因为会影响 index，先标记
                                toRemove.add(i);
                            } else {
                                // sw $t0, addr ... lw $t1, addr -> move $t1, $t0
                                insts.set(i, new MipsMove(loadDest, storedReg));
                                removedCount++;
                                // 替换后变成了 move，需要 invalidate dest 在 map 中的记录
                                invalidateRegisterInStoreMap(storeMap, loadDest);
                            }
                            continue;
                        }
                    }
                    // LW 会重定义寄存器
                    invalidateRegisterInStoreMap(storeMap, loadDest);
                }
            } else {
                // 其他指令：检查 defs 是否 invalidate storeMap
                for (Operand def : inst.getDef()) {
                    invalidateRegisterInStoreMap(storeMap, def);
                }
            }
        }

        // 倒序删除被标记的指令
        for (int k = toRemove.size() - 1; k >= 0; k--) {
            insts.remove((int) toRemove.get(k));
            removedCount++;
        }
    }

    private void invalidateRegisterInStoreMap(Map<MemoryLocation, Operand> storeMap, Operand reg) {
        storeMap.entrySet().removeIf(entry ->
                entry.getValue().equals(reg) ||  // value 失效
                        entry.getKey().base.equals(reg)  // base 地址失效
        );
    }

    // ==================== [Extreme] P4: 极端激进优化 ====================

    /**
     * [P4] 块内寄存器重定义消除 (DCE)
     * 模式：
     * addu $t0, $t1, $t2
     * ... (指令未读取 $t0) ...
     * li $t0, 10
     * <p>
     * 动作：删除第一条 addu 指令
     * 限制：仅针对临时寄存器，遇到分支停止
     */
    private void optimizeRedundantDefs(MipsBasicBlock block) {
        List<MipsInstruction> insts = block.getInstructions();
        // 记录寄存器的最后一次定义位置: Reg -> Index
        Map<Operand, Integer> lastDefMap = new HashMap<>();
        Set<Integer> toRemove = new HashSet<>();

        for (int i = 0; i < insts.size(); i++) {
            MipsInstruction inst = insts.get(i);

            // 遇到分支/跳转/调用：清空所有记录（控制流不确定性）
            if (inst instanceof MipsBranch || inst instanceof MipsSyscall) {
                lastDefMap.clear();
                continue;
            }

            // 检查 Use：如果在定义后被使用，则该定义有效，从 map 中移除
            for (Operand use : inst.getUse()) {
                lastDefMap.remove(use);
            }

            // 检查 Def
            for (Operand def : inst.getDef()) {
                // 如果该寄存器之前被定义过，且中间未被使用 -> 前一次定义是死代码
                if (lastDefMap.containsKey(def)) {
                    int prevDefIdx = lastDefMap.get(def);
                    // 只要不是特殊寄存器，就允许删除冗余定义 (激进策略)
                    if (!isSpecialRegister(def)) {
                        toRemove.add(prevDefIdx);
                    }
                }
                // 更新最后定义位置
                lastDefMap.put(def, i);
            }
        }

        // 删除死指令
        if (!toRemove.isEmpty()) {
            removeByIndices(insts, toRemove);
            removedCount += toRemove.size();
        }
    }

    /**
     * [P4] 深度死存储消除 (Deep Dead Store Elimination)
     * 模式：
     * sw $t0, 4($sp)
     * ...
     * sw $t1, 4($sp)
     * <p>
     * 动作：删除第一条 sw
     * 激进点：忽略中间不同基址的 Load (假设无别名)
     */
    private void deepDeadStoreElimination(MipsBasicBlock block) {
        List<MipsInstruction> insts = block.getInstructions();
        Set<Integer> toRemove = new HashSet<>();

        for (int i = 0; i < insts.size(); i++) {
            if (toRemove.contains(i)) continue;
            MipsInstruction cur = insts.get(i);

            if (!(cur instanceof MipsLoadStore sw) || sw.getType() != MipsLoadStore.Type.SW) {
                continue;
            }

            Operand base = sw.getBase();
            int offset = sw.getOffset();
            Operand storedReg = sw.getRt();

            // 向后扫描
            for (int j = i + 1; j < insts.size(); j++) {
                if (toRemove.contains(j)) continue;
                MipsInstruction future = insts.get(j);

                // 终止条件：控制流变化
                if (future instanceof MipsBranch || future instanceof MipsSyscall) break;

                // 终止条件：使用被存储的寄存器 (store $t0 ... add $t0 ... -> $t0 值可能变了，再 store 就不是覆盖了)
                // 等等，如果 $t0 被重写，原来的值还在内存里。
                // 重要的是 base 寄存器不能变。
                if (definesRegister(future, base)) break;

                // 检查 Load
                if (future instanceof MipsLoadStore lw && lw.getType() == MipsLoadStore.Type.LW) {
                    // 同地址 Load：不能删除前面的 Store
                    if (lw.getBase().equals(base) && lw.getOffset() == offset) {
                        break;
                    }
                    // 不同地址 Load：激进假设无别名，继续扫描
                    continue;
                }

                // 检查 Store
                if (future instanceof MipsLoadStore nextSw && nextSw.getType() == MipsLoadStore.Type.SW) {
                    if (nextSw.getBase().equals(base) && nextSw.getOffset() == offset) {
                        // 发现覆盖 Store！删除当前的 (第一个) Store
                        toRemove.add(i);
                        removedCount++;
                        break;
                    }
                    // 不同地址 Store：不影响
                }
            }
        }

        if (!toRemove.isEmpty()) {
            removeByIndices(insts, toRemove);
        }
    }
    


    /**
     * [P4] 强度削减 (Strength Reduction)
     * mul $t0, $t1, 2 -> sll $t0, $t1, 1
     * div $t0, $t1, 2 -> sra $t0, $t1, 1 (注意: 对负数处理不同，Extreme模式下忽略)
     */
    private void optimizeStrengthReduction(MipsBasicBlock block) {
        List<MipsInstruction> insts = block.getInstructions();
        
        for (int i = 0; i < insts.size(); i++) {
            MipsInstruction inst = insts.get(i);
            
            if (inst instanceof MipsBinary bin && bin.getImm() != null) {
                String op = bin.getOp();
                int imm = bin.getImm();
                
                // mul $t0, $t1, power_of_2
                if (op.equals("mul")) {
                    if (imm == 1) {
                        // mul $t0, $t1, 1 -> move $t0, $t1
                        insts.set(i, new MipsMove(bin.getRd(), bin.getRs()));
                        removedCount++;
                        continue;
                    } else if (imm > 0 && (imm & (imm - 1)) == 0) {
                        // mul $t0, $t1, 2^k -> sll $t0, $t1, k
                        int shift = Integer.numberOfTrailingZeros(imm);
                        insts.set(i, new MipsBinary("sll", bin.getRd(), bin.getRs(), shift));
                        removedCount++;
                        continue;
                    }
                }
                
                // div $t0, $t1, power_of_2 (Extreme Only: 忽略负数舍入差异)
                if (op.equals("div") && imm > 0 && (imm & (imm - 1)) == 0) {
                     int shift = Integer.numberOfTrailingZeros(imm);
                     insts.set(i, new MipsBinary("sra", bin.getRd(), bin.getRs(), shift));
                     removedCount++;
                     continue;
                }
            }
        }
    }

    /**
     * [P1.5] 跳转线程化 (Jump Threading)
     * j L1 -> (L1: j L2) => j L2
     */
    private void optimizeJumpThreading(List<MipsBasicBlock> blocks) {
        // 构建 Label -> Block 的映射
        Map<String, MipsBasicBlock> labelMap = new HashMap<>();
        for (MipsBasicBlock block : blocks) {
            if (block.getLabel() != null) {
                labelMap.put(block.getLabel(), block);
            }
        }
        
        for (MipsBasicBlock block : blocks) {
            List<MipsInstruction> insts = block.getInstructions();
            if (insts.isEmpty()) continue;
            
            MipsInstruction last = insts.get(insts.size() - 1);
            if (last instanceof MipsBranch br && "j".equals(br.getOp())) {
                String targetLabel = br.getLabel();
                
                // 查找目标块
                MipsBasicBlock targetBlock = labelMap.get(targetLabel);
                if (targetBlock == null) continue;
                
                // 检查目标块是否只包含无条件跳转
                // 允许目标块为空（直接穿透到下一块，虽然 MipsBasicBlock 通常不为空）
                // 或者是: label: j L2
                
                List<MipsInstruction> targetInsts = targetBlock.getInstructions();
                // 简单起见，只处理目标块只有一条 j 指令的情况
                if (targetInsts.size() == 1 && targetInsts.get(0) instanceof MipsBranch nextBr 
                        && "j".equals(nextBr.getOp())) {
                    
                    String finalTarget = nextBr.getLabel();
                    if (finalTarget != null && !finalTarget.equals(targetLabel)) {
                        // j L1 -> j L2
                        insts.set(insts.size() - 1, new MipsBranch("j", finalTarget));
                        removedCount++;
                    }
                }
            }
        }
    }
    
    /**
     * 增强内存优化：跨指令的 Store-Load 跟踪
     * 
     * 在单个基本块内，跟踪最近的 store 操作。
     * 当发现后续的 load 访问同一地址时：
     * - 如果 store 的源寄存器值仍然有效，用 move 替代 load
     * - 如果 store 和 load 使用同一寄存器，直接删除 load
     * 
     * 安全限制（关键！）：
     * - 只对栈地址（base 为 $sp 或 $fp）进行优化
     * - 原因：堆/数组地址存在别名问题，不同的 base 寄存器可能指向同一内存
     * - 遇到非栈的 SW：清空 storeMap（可能覆盖任何地址）
     * - 遇到函数调用（jal）：清空所有内存状态
     * - 寄存器被重新定义：从 storeMap 中移除该寄存器的记录
     */
    private void optimizeMemoryAccess(MipsBasicBlock block) {
        List<MipsInstruction> insts = block.getInstructions();
        
        // 内存状态跟踪：地址 -> 存储的寄存器（仅限栈地址）
        Map<MemoryLocation, Operand> storeMap = new HashMap<>();
        
        // 使用索引遍历以便安全修改
        for (int i = 0; i < insts.size(); i++) {
            MipsInstruction inst = insts.get(i);
            
            // 函数调用：清空所有内存状态（可能修改全局内存）
            if (inst instanceof MipsBranch br) {
                String op = br.getOp();
                if ("jal".equals(op) || "jalr".equals(op)) {
                    storeMap.clear();
                    continue;
                }
            }
            
            // Syscall：清空所有内存状态
            if (inst instanceof MipsSyscall) {
                storeMap.clear();
                continue;
            }
            
            // 处理 Load/Store 指令
            if (inst instanceof MipsLoadStore ls) {
                Operand base = ls.getBase();
                boolean isStackAddr = isStackAddress(base);
                
                if (ls.getType() == MipsLoadStore.Type.SW) {
                    if (isStackAddr) {
                        // 栈地址的 Store: 记录存储的寄存器
                        MemoryLocation loc = new MemoryLocation(base, ls.getOffset());
                        storeMap.put(loc, ls.getRt());
                    } else {
                        // 非栈地址的 Store: 可能覆盖任何堆内存，必须清空 storeMap
                        // 这是关键的安全措施！
                        // [Extreme] 如果启用极端优化，跳过这个清空
                        if (!Config.ENABLE_EXTREME_PEEPHOLE) {
                            storeMap.clear();
                        }
                    }
                } else if (ls.getType() == MipsLoadStore.Type.LW) {
                    Operand loadDest = ls.getRt();
                    
                    if (isStackAddr) {
                        // 栈地址的 Load: 检查是否可以优化
                        MemoryLocation loc = new MemoryLocation(base, ls.getOffset());
                        Operand storedReg = storeMap.get(loc);
                        
                        if (storedReg != null) {
                            if (storedReg.equals(loadDest)) {
                                // sw $t0, addr + lw $t0, addr -> 删除 lw
                                insts.remove(i);
                                i--; // 调整索引
                                removedCount++;
                            } else {
                                // sw $t0, addr + lw $t1, addr -> move $t1, $t0
                                insts.set(i, new MipsMove(loadDest, storedReg));
                                removedCount++;
                                invalidateRegisterInStoreMap(storeMap, loadDest);
                            }
                            continue;
                        }
                    }
                    // 未优化的 LW 会重定义寄存器
                    invalidateRegisterInStoreMap(storeMap, loadDest);
                }
            } else {
                // 其他指令：检查是否会 invalidate 某些 storeMap 条目
                for (Operand def : inst.getDef()) {
                    invalidateRegisterInStoreMap(storeMap, def);
                }
            }
        }
    }
    
    /**
     * 判断是否为栈地址（base 为 $sp 或 $fp）
     * 
     * 只有栈地址才能安全进行跨指令的 Store-Load 优化，
     * 因为栈地址不存在别名问题。
     */
    private boolean isStackAddress(Operand base) {
        return base == MipsRegister.SP || base == MipsRegister.FP;
    }
    
    /**
     * 检查操作数是否为临时寄存器 $t0-$t9
     */
    private boolean isTempRegister(Operand operand) {
        if (!(operand instanceof MipsRegister)) {
            return false;
        }
        MipsRegister reg = (MipsRegister) operand;
        int id = reg.getId();
        // $t0-$t7: id 8-15, $t8-$t9: id 24-25
        return (id >= 8 && id <= 15) || (id >= 24 && id <= 25);
    }
    
    /**
     * 检查操作数是否为参数寄存器 $a0-$a3
     */
    private boolean isArgRegister(Operand operand) {
        if (!(operand instanceof MipsRegister)) {
            return false;
        }
        MipsRegister reg = (MipsRegister) operand;
        int id = reg.getId();
        // $a0-$a3: id 4-7
        return id >= 4 && id <= 7;
    }
    
    // ==================== [P5] 更激进的极端优化 ====================
    
    /**
     * [P5] Li + Addiu 合并
     * 模式: li $t0, X + addiu $t1, $t0, Y -> li $t1, X+Y (如果 $t0 不再使用)
     */
    private void optimizeLiCombine(MipsBasicBlock block) {
        List<MipsInstruction> insts = block.getInstructions();
        Set<Integer> toRemove = new HashSet<>();
        
        for (int i = 0; i < insts.size() - 1; i++) {
            if (toRemove.contains(i)) continue;
            MipsInstruction cur = insts.get(i);
            MipsInstruction next = insts.get(i + 1);
            
            // 检查 li + addiu 模式
            if (cur instanceof MipsLi li && next instanceof MipsBinary bin) {
                if (!"addiu".equals(bin.getOp()) && !"addi".equals(bin.getOp())) continue;
                
                Operand liDest = li.getRd();
                Operand binRs = bin.getRs();
                Integer binImm = bin.getImm();
                
                // 检查 addiu 的源是否是 li 的目标
                if (liDest.equals(binRs) && binImm != null) {
                    // 检查 li 的目标在 addiu 之后是否还被使用
                    if (!isRegisterUsedAfter(liDest, i + 2, insts) || liDest.equals(bin.getRd())) {
                        int newImm = li.getImm() + binImm;
                        // 检查新立即数是否在 16 位范围内
                        if (newImm >= -32768 && newImm <= 32767) {
                            // 替换为单条 li
                            insts.set(i + 1, new MipsLi(bin.getRd(), newImm));
                            toRemove.add(i);
                            removedCount++;
                        }
                    }
                }
            }
        }
        
        if (!toRemove.isEmpty()) {
            removeByIndices(insts, toRemove);
        }
    }
    
    /**
     * [P5] 双 Move 消除
     * 模式: move $a, $b + move $c, $a -> move $c, $b (如果 $a 不再使用)
     */
    private void optimizeDoubleMoves(MipsBasicBlock block) {
        List<MipsInstruction> insts = block.getInstructions();
        Set<Integer> toRemove = new HashSet<>();
        
        for (int i = 0; i < insts.size() - 1; i++) {
            if (toRemove.contains(i)) continue;
            MipsInstruction cur = insts.get(i);
            MipsInstruction next = insts.get(i + 1);
            
            if (cur instanceof MipsMove move1 && next instanceof MipsMove move2) {
                List<Operand> defs1 = move1.getDef();
                List<Operand> uses1 = move1.getUse();
                List<Operand> defs2 = move2.getDef();
                List<Operand> uses2 = move2.getUse();
                
                if (defs1.isEmpty() || uses1.isEmpty() || defs2.isEmpty() || uses2.isEmpty()) continue;
                
                Operand a = defs1.get(0);  // move1: $a <- $b
                Operand b = uses1.get(0);
                Operand c = defs2.get(0);  // move2: $c <- $a
                Operand a2 = uses2.get(0);
                
                // 检查 move2 的源是否是 move1 的目标
                if (a.equals(a2)) {
                    // 检查 $a 在 move2 之后是否还被使用
                    if (!isRegisterUsedAfter(a, i + 2, insts) || a.equals(c)) {
                        // 替换 move2 为 move $c, $b
                        insts.set(i + 1, new MipsMove(c, b));
                        toRemove.add(i);
                        removedCount++;
                    }
                }
            }
        }
        
        if (!toRemove.isEmpty()) {
            removeByIndices(insts, toRemove);
        }
    }
    
    /**
     * [P5] 激进 Load 消除
     * 模式: lw $t0, addr ... lw $t1, addr -> lw $t0, addr ... move $t1, $t0
     * 激进点: 忽略中间不同地址的 Store（假设无别名）
     */
    private void optimizeAggressiveLoadElimination(MipsBasicBlock block) {
        List<MipsInstruction> insts = block.getInstructions();
        // 跟踪最近的 Load: 地址 -> (索引, 目标寄存器)
        Map<String, int[]> loadMap = new HashMap<>(); // addr -> [index, regId]
        
        for (int i = 0; i < insts.size(); i++) {
            MipsInstruction inst = insts.get(i);
            
            // 控制流变化: 清空状态
            if (inst instanceof MipsBranch || inst instanceof MipsSyscall) {
                loadMap.clear();
                continue;
            }
            
            if (inst instanceof MipsLoadStore ls) {
                String addr = ls.getBase().toString() + "_" + ls.getOffset();
                
                if (ls.getType() == MipsLoadStore.Type.SW) {
                    // 同地址的 Store: 失效该地址的 Load 记录
                    loadMap.remove(addr);
                    // 激进: 不清空其他地址（假设无别名）
                } else if (ls.getType() == MipsLoadStore.Type.LW) {
                    Operand loadDest = ls.getRt();
                    
                    // 检查是否有之前的 Load 到同一地址
                    if (loadMap.containsKey(addr)) {
                        int[] prev = loadMap.get(addr);
                        int prevIdx = prev[0];
                        Operand prevDest = insts.get(prevIdx) instanceof MipsLoadStore prevLs ? prevLs.getRt() : null;
                        
                        if (prevDest != null && !definesRegister(insts, prevIdx + 1, i, prevDest)) {
                            // 前一个 Load 的目标寄存器仍然有效
                            if (prevDest.equals(loadDest)) {
                                // 同寄存器: 删除当前 Load
                                insts.remove(i);
                                i--;
                                removedCount++;
                                continue;
                            } else {
                                // 不同寄存器: 替换为 Move
                                insts.set(i, new MipsMove(loadDest, prevDest));
                                removedCount++;
                            }
                        }
                    }
                    
                    // 记录当前 Load
                    loadMap.put(addr, new int[]{i, 0});
                    
                    // 如果 Load 重定义了某个地址的 base，失效该地址
                    invalidateByBase(loadMap, loadDest);
                }
            } else {
                // 其他指令: 检查是否重定义了某个 Load 的目标寄存器
                for (Operand def : inst.getDef()) {
                    invalidateByReg(loadMap, def, insts);
                }
            }
        }
    }
    
    /**
     * 检查在 [start, end) 范围内是否有指令重定义了指定寄存器
     */
    private boolean definesRegister(List<MipsInstruction> insts, int start, int end, Operand reg) {
        for (int i = start; i < end && i < insts.size(); i++) {
            if (definesRegister(insts.get(i), reg)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 从 loadMap 中移除以指定寄存器为 base 的条目
     */
    private void invalidateByBase(Map<String, int[]> loadMap, Operand base) {
        String prefix = base.toString() + "_";
        loadMap.keySet().removeIf(key -> key.startsWith(prefix));
    }
    
    /**
     * 从 loadMap 中移除目标寄存器被重定义的条目
     */
    private void invalidateByReg(Map<String, int[]> loadMap, Operand reg, List<MipsInstruction> insts) {
        loadMap.entrySet().removeIf(entry -> {
            int idx = entry.getValue()[0];
            if (idx < insts.size() && insts.get(idx) instanceof MipsLoadStore ls) {
                return ls.getRt().equals(reg);
            }
            return false;
        });
    }
}
