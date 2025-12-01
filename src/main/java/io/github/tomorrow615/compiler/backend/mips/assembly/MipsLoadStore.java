package io.github.tomorrow615.compiler.backend.mips.assembly;

import io.github.tomorrow615.compiler.backend.mips.MipsRegister;

public class MipsLoadStore extends MipsInstruction {
    public enum Type {
        LW, // Load Word
        SW  // Store Word
    }

    private final Type type;
    private final MipsRegister rt;   // 要加载/存储的目标寄存器
    private final MipsRegister base; // 基地址寄存器 (通常是 $sp 或 $fp)
    private final int offset;        // 偏移量

    // 例如: lw $t0, 4($sp)
    public MipsLoadStore(Type type, MipsRegister rt, MipsRegister base, int offset) {
        this.type = type;
        this.rt = rt;
        this.base = base;
        this.offset = offset;
    }

    @Override
    public String toString() {
        // 输出格式: lw $t0, 4($sp)
        return String.format("%s %s, %d(%s)", type.name().toLowerCase(), rt, offset, base);
    }
}