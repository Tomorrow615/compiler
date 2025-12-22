package io.github.tomorrow615.compiler.backend.mips;

import io.github.tomorrow615.compiler.backend.mips.assembly.MipsBranch;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsInstruction;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsBasicBlock;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MipsModule {
    // 存放全局变量定义的汇编字符串，例如 "a: .word 0"
    // Phase 1 简单处理，直接存 String 即可，或者后续定义 MipsGlobalData 类
    private final List<String> dataSection;

    // 存放函数
    private final List<MipsFunction> textSection;

    public MipsModule() {
        this.dataSection = new ArrayList<>();
        this.textSection = new ArrayList<>();
    }

    public void addGlobalData(String dataLine) {
        this.dataSection.add(dataLine);
    }

    public void addFunction(MipsFunction function) {
        this.textSection.add(function);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // 1. .data 段 - 仅当有数据时才输出
        if (!dataSection.isEmpty()) {
            sb.append(".data\n");
            for (String data : dataSection) {
                sb.append(data).append("\n");
            }
            sb.append("\n");
        }

        // 2. 收集所有被跳转引用的标签
        Set<String> referencedLabels = collectReferencedLabels();

        // 3. .text 段
        sb.append(".text\n");
        // 初始化代码（如有需要，如调用 main），通常 main 是入口，MARS 会自动找 main
        // 这里直接输出所有函数
        for (MipsFunction func : textSection) {
            sb.append("\n# === Function: ").append(func.getName()).append(" ===\n");
            
            List<MipsBasicBlock> blocks = func.getBlocks();
            for (int i = 0; i < blocks.size(); i++) {
                MipsBasicBlock block = blocks.get(i);
                String label = block.getLabel();
                
                // 判断是否需要输出标签：
                // 1. 函数入口（第一个基本块）必须输出
                // 2. 被跳转引用的标签必须输出
                boolean needLabel = (i == 0) || referencedLabels.contains(label);
                sb.append(block.toString(needLabel));
            }
        }

        return sb.toString();
    }

    /**
     * 收集所有被跳转指令引用的标签
     */
    private Set<String> collectReferencedLabels() {
        Set<String> labels = new HashSet<>();
        for (MipsFunction func : textSection) {
            for (MipsBasicBlock block : func.getBlocks()) {
                for (MipsInstruction inst : block.getInstructions()) {
                    if (inst instanceof MipsBranch branch) {
                        String target = branch.getLabel();
                        if (target != null) {
                            labels.add(target);
                        }
                    }
                }
            }
        }
        return labels;
    }
}