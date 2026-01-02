package io.github.tomorrow615.compiler.backend.mips.optimize;

import io.github.tomorrow615.compiler.backend.mips.MipsModule;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsBranch;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsInstruction;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsBasicBlock;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;

import java.util.*;

/**
 * 基本块布局优化 (Block Layout Optimization)
 * 
 * 通过重排基本块顺序来最大化 Fallthrough，减少无条件跳转 (j) 指令。
 * 
 * 安全性保证：
 * 1. 使用 visited 集合防止死循环
 * 2. 覆盖所有分支修复情况 (Case A/B/C)
 * 3. 保留原有 Label，只修改块顺序和分支指令
 */
public class BlockLayout {

    private final MipsModule module;

    // 条件跳转取反映射
    private static final Map<String, String> INVERT_OP = new HashMap<>();
    static {
        INVERT_OP.put("beq", "bne");
        INVERT_OP.put("bne", "beq");
        INVERT_OP.put("bnez", "beqz");
        INVERT_OP.put("beqz", "bnez");
        INVERT_OP.put("bge", "blt");
        INVERT_OP.put("blt", "bge");
        INVERT_OP.put("bgt", "ble");
        INVERT_OP.put("ble", "bgt");
        INVERT_OP.put("bgeu", "bltu");
        INVERT_OP.put("bltu", "bgeu");
        INVERT_OP.put("bgtu", "bleu");
        INVERT_OP.put("bleu", "bgtu");
    }

    public BlockLayout(MipsModule module) {
        this.module = module;
    }

    public void optimize() {
        for (MipsFunction func : module.getFunctions()) {
            optimizeFunction(func);
        }
    }

    private void optimizeFunction(MipsFunction func) {
        List<MipsBasicBlock> blocks = func.getBlocks();
        if (blocks.size() <= 1) {
            return; // 单块函数无需优化
        }

        // Step 1: 构建 Label -> Block 映射 和 原始 Fallthrough 关系
        Map<String, MipsBasicBlock> labelToBlock = new HashMap<>();
        Map<MipsBasicBlock, MipsBasicBlock> originalFallthrough = new HashMap<>();

        for (int i = 0; i < blocks.size(); i++) {
            MipsBasicBlock block = blocks.get(i);
            labelToBlock.put(block.getLabel(), block);
            if (i + 1 < blocks.size()) {
                originalFallthrough.put(block, blocks.get(i + 1));
            }
        }

        // Step 2: 贪心重排
        List<MipsBasicBlock> newOrder = new ArrayList<>();
        Set<MipsBasicBlock> visited = new HashSet<>();

        // 从入口块开始
        MipsBasicBlock current = blocks.get(0);
        newOrder.add(current);
        visited.add(current);

        while (visited.size() < blocks.size()) {
            MipsBasicBlock next = findBestSuccessor(current, labelToBlock, originalFallthrough, visited);
            
            if (next == null) {
                // Fallback: 找原始列表中第一个未访问的块
                for (MipsBasicBlock b : blocks) {
                    if (!visited.contains(b)) {
                        next = b;
                        break;
                    }
                }
            }

            if (next == null) {
                break; // 所有块都处理完了
            }

            newOrder.add(next);
            visited.add(next);
            current = next;
        }

        // Step 3: 分支修复
        fixupBranches(newOrder, labelToBlock, originalFallthrough);

        // Step 4: 替换块列表
        blocks.clear();
        blocks.addAll(newOrder);
    }

    /**
     * 按优先级寻找最佳后继块
     * Priority 1: 原 Fallthrough 块 (False Successor)
     * Priority 2: 跳转目标块 (True Successor)
     */
    private MipsBasicBlock findBestSuccessor(
            MipsBasicBlock current,
            Map<String, MipsBasicBlock> labelToBlock,
            Map<MipsBasicBlock, MipsBasicBlock> originalFallthrough,
            Set<MipsBasicBlock> visited) {

        List<MipsInstruction> insts = current.getInstructions();
        if (insts.isEmpty()) {
            // 空块，原 Fallthrough
            MipsBasicBlock fallthrough = originalFallthrough.get(current);
            if (fallthrough != null && !visited.contains(fallthrough)) {
                return fallthrough;
            }
            return null;
        }

        MipsInstruction lastInst = insts.get(insts.size() - 1);
        
        // Priority 1: 原 Fallthrough
        MipsBasicBlock fallthrough = originalFallthrough.get(current);
        
        if (lastInst instanceof MipsBranch branch) {
            String op = branch.getOp();
            String label = branch.getLabel();

            if (op.equals("j")) {
                // 无条件跳转，优先选目标块
                MipsBasicBlock target = labelToBlock.get(label);
                if (target != null && !visited.contains(target)) {
                    return target;
                }
            } else if (op.equals("jr") || op.equals("jal") || op.equals("jalr")) {
                // Return 或 Call，无后继优选
                // 尝试 Fallthrough (如果有的话，通常 jr 之后没有)
                if (fallthrough != null && !visited.contains(fallthrough)) {
                    return fallthrough;
                }
            } else if (isConditionalBranch(op)) {
                // 条件跳转：优先选 Fallthrough，其次选目标
                if (fallthrough != null && !visited.contains(fallthrough)) {
                    return fallthrough;
                }
                MipsBasicBlock target = labelToBlock.get(label);
                if (target != null && !visited.contains(target)) {
                    return target;
                }
            }
        } else {
            // 非跳转指令结尾，隐式 Fallthrough
            if (fallthrough != null && !visited.contains(fallthrough)) {
                return fallthrough;
            }
        }

        return null;
    }

    /**
     * 修复重排后的分支指令
     */
    private void fixupBranches(
            List<MipsBasicBlock> newOrder,
            Map<String, MipsBasicBlock> labelToBlock,
            Map<MipsBasicBlock, MipsBasicBlock> originalFallthrough) {

        for (int i = 0; i < newOrder.size(); i++) {
            MipsBasicBlock current = newOrder.get(i);
            MipsBasicBlock nextPhysics = (i + 1 < newOrder.size()) ? newOrder.get(i + 1) : null;

            List<MipsInstruction> insts = current.getInstructions();
            if (insts.isEmpty()) {
                // 空块，检查是否需要补跳转
                MipsBasicBlock originalNext = originalFallthrough.get(current);
                if (originalNext != null && originalNext != nextPhysics) {
                    insts.add(new MipsBranch("j", originalNext.getLabel()));
                }
                continue;
            }

            MipsInstruction lastInst = insts.get(insts.size() - 1);

            if (!(lastInst instanceof MipsBranch branch)) {
                // Case C: 非跳转指令结尾 (Implicit Fallthrough)
                MipsBasicBlock originalNext = originalFallthrough.get(current);
                if (originalNext != null && originalNext != nextPhysics) {
                    // 原 Fallthrough 被打破，补 j
                    insts.add(new MipsBranch("j", originalNext.getLabel()));
                }
                continue;
            }

            String op = branch.getOp();
            String targetLabel = branch.getLabel();
            MipsBasicBlock targetBlock = (targetLabel != null) ? labelToBlock.get(targetLabel) : null;

            if (op.equals("j")) {
                // Case A: 无条件跳转
                if (targetBlock == nextPhysics) {
                    // 目标就是下一条，删除 j
                    insts.remove(insts.size() - 1);
                }
                // 否则保持不变
            } else if (op.equals("jr") || op.equals("jal") || op.equals("jalr")) {
                // Case D: Return 或 Call，不处理
            } else if (isConditionalBranch(op)) {
                // Case B: 条件跳转
                MipsBasicBlock originalFallthroughBlock = originalFallthrough.get(current);

                if (targetBlock == nextPhysics) {
                    // B1: 跳转目标变成了下一条 -> 翻转条件
                    String invertedOp = INVERT_OP.get(op);
                    if (invertedOp != null && originalFallthroughBlock != null) {
                        // 替换为翻转后的分支指令
                        MipsBranch newBranch = createInvertedBranch(branch, invertedOp, originalFallthroughBlock.getLabel());
                        insts.set(insts.size() - 1, newBranch);
                    }
                    // 如果没有映射或没有 Fallthrough，保持原样（安全降级）
                } else if (originalFallthroughBlock == nextPhysics) {
                    // B2: 原 Fallthrough 仍然是下一条，完美，不做修改
                } else {
                    // B3: 都不是下一条，需要补 j 到原 Fallthrough
                    if (originalFallthroughBlock != null) {
                        insts.add(new MipsBranch("j", originalFallthroughBlock.getLabel()));
                    }
                }
            }
        }
    }

    private boolean isConditionalBranch(String op) {
        return INVERT_OP.containsKey(op);
    }

    /**
     * 创建翻转条件的分支指令
     */
    private MipsBranch createInvertedBranch(MipsBranch original, String newOp, String newLabel) {
        // MipsBranch 有多种构造函数，需要根据原指令的格式选择
        if (original.getRs() != null) {
            // 检查是否有 rt (双寄存器: beq $t0, $t1, label)
            // 通过 toString 判断格式，或者直接访问字段
            // 由于 rt 是 private，我们用原始指令的 Use 信息重建
            List<io.github.tomorrow615.compiler.backend.mips.operand.Operand> uses = original.getUse();
            if (uses.size() == 2) {
                // 双寄存器条件跳转
                return new MipsBranch(newOp, uses.get(0), uses.get(1), newLabel);
            } else if (uses.size() == 1) {
                // 单寄存器条件跳转 (bnez, beqz)
                return new MipsBranch(newOp, uses.get(0), newLabel);
            }
        }
        // Fallback: 无寄存器 (不应该发生)
        return new MipsBranch(newOp, newLabel);
    }
}
