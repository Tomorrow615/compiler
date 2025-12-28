package io.github.tomorrow615.compiler.backend.mips.structure;

import io.github.tomorrow615.compiler.backend.mips.assembly.MipsBranch;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsInstruction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MipsFunction {
    private final String name;
    private final List<MipsBasicBlock> blocks;

    public MipsFunction(String name) {
        this.name = name;
        this.blocks = new ArrayList<>();
    }

    public void addBasicBlock(MipsBasicBlock block) {
        this.blocks.add(block);
    }

    public String getName() {
        return name;
    }

    public List<MipsBasicBlock> getBlocks() {
        return blocks;
    }

    public void buildCFG() {
        Map<String, MipsBasicBlock> labelToBlock = new HashMap<>();
        for (MipsBasicBlock block : blocks) {
            labelToBlock.put(block.getLabel(), block);
        }

        for (int i = 0; i < blocks.size(); i++) {
            MipsBasicBlock currentBlock = blocks.get(i);
            List<MipsInstruction> insts = currentBlock.getInstructions();
            
            if (insts.isEmpty()) {
                // 空块，逻辑上应该 fallthrough 到下个块
                if (i + 1 < blocks.size()) {
                    MipsBasicBlock nextBlock = blocks.get(i + 1);
                    link(currentBlock, nextBlock);
                }
                continue;
            }

            MipsInstruction lastInst = insts.get(insts.size() - 1);
            if (lastInst instanceof MipsBranch branch) {
                String label = branch.getLabel();
                
                // 1. 处理跳转目标 (Successor 1)
                if (label != null) {
                    MipsBasicBlock targetBlock = labelToBlock.get(label);
                    // 注意：这里的 label 可能是函数名(递归调用)或其他函数的label。
                    // 只有当目标在当前函数内时，才算作 CFG 的边。
                    if (targetBlock != null) {
                        link(currentBlock, targetBlock);
                    }
                }

                // 2. 处理 Fallthrough (Successor 2)
                // 如果是条件跳转 (bne, beq, bnez 等) 或者不是跳转指令(虽然这里是 instanceof Branch)
                // 无条件跳转 (j, jr) 不会 fallthrough
                String op = branch.toString().split(" ")[0]; // 简单获取 op
                boolean isUnconditional = op.equals("j") || op.equals("jr");
                
                if (!isUnconditional) {
                    if (i + 1 < blocks.size()) {
                        link(currentBlock, blocks.get(i + 1));
                    }
                }
            } else {
                // 最后一条指令不是跳转，自然顺序执行 (Fallthrough)
                if (i + 1 < blocks.size()) {
                    link(currentBlock, blocks.get(i + 1));
                }
            }
        }
    }

    private void link(MipsBasicBlock pred, MipsBasicBlock succ) {
        pred.addSuccessor(succ);
        succ.addPredecessor(pred);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        // 函数名作为注释或直接作为入口标签的一部分（具体由第一个Block的标签决定）
        // 这里我们简单打印一个注释分隔符
        sb.append("\n# === Function: ").append(name).append(" ===\n");

        for (MipsBasicBlock block : blocks) {
            sb.append(block.toString()); // 基本块自带换行
        }
        return sb.toString();
    }
}