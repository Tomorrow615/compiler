package io.github.tomorrow615.compiler.backend.mips.assembly;

import io.github.tomorrow615.compiler.backend.mips.MipsRegister;

public class MipsMove extends MipsInstruction {
    private final MipsRegister dst; // 目标
    private final MipsRegister src; // 源

    // 例如: move $a0, $t0
    public MipsMove(MipsRegister dst, MipsRegister src) {
        this.dst = dst;
        this.src = src;
    }

    @Override
    public String toString() {
        return String.format("move %s, %s", dst, src);
    }
}