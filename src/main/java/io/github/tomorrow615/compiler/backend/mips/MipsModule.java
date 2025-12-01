package io.github.tomorrow615.compiler.backend.mips;

import io.github.tomorrow615.compiler.backend.mips.assembly.MipsInstruction; // 引入指令基类用于data段（虽然通常不是指令，但暂用String或特定类）
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;
import java.util.ArrayList;
import java.util.List;

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

        // 1. .data 段
        sb.append(".data\n");
        for (String data : dataSection) {
            sb.append(data).append("\n");
        }
        sb.append("\n");

        // 2. .text 段
        sb.append(".text\n");
        // 初始化代码（如有需要，如调用 main），通常 main 是入口，MARS 会自动找 main
        // 这里直接输出所有函数
        for (MipsFunction func : textSection) {
            sb.append(func.toString());
        }

        return sb.toString();
    }
}