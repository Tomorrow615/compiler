package io.github.tomorrow615.compiler.backend.mips.assembly;

import io.github.tomorrow615.compiler.backend.mips.operand.Operand;

import java.util.List;

public class MipsMove extends MipsInstruction {
    private Operand dst; // 目标
    private Operand src; // 源

    // 例如: move $a0, $t0
    public MipsMove(Operand dst, Operand src) {
        this.dst = dst;
        this.src = src;
    }

    @Override
    public String toString() {
        return String.format("move %s, %s", dst.toAsm(), src.toAsm());
    }

    // ========== Def/Use 接口实现 ==========

    @Override
    public List<Operand> getDef() {
        // move rd, rs -> 定义了 rd
        return List.of(dst);
    }

    @Override
    public List<Operand> getUse() {
        // move rd, rs -> 使用了 rs
        return List.of(src);
    }

    @Override
    public void replaceDef(Operand oldOp, Operand newOp) {
        if (dst.equals(oldOp)) {
            dst = newOp;
        }
    }

    @Override
    public void replaceUse(Operand oldOp, Operand newOp) {
        if (src.equals(oldOp)) {
            src = newOp;
        }
    }
}