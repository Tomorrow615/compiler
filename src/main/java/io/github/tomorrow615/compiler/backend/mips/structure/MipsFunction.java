package io.github.tomorrow615.compiler.backend.mips.structure;

import java.util.ArrayList;
import java.util.List;

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