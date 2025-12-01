package io.github.tomorrow615.compiler.backend.mips.assembly;

import io.github.tomorrow615.compiler.backend.mips.MipsRegister;

public class MipsLi extends MipsInstruction {
    private final MipsRegister rd; // 目标寄存器
    private final int imm;         // 立即数

    // 例如: li $t0, 100
    public MipsLi(MipsRegister rd, int imm) {
        this.rd = rd;
        this.imm = imm;
    }

    @Override
    public String toString() {
        return String.format("li %s, %d", rd, imm);
    }
}