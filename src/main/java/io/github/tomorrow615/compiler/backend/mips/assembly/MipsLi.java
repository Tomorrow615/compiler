package io.github.tomorrow615.compiler.backend.mips.assembly;

import io.github.tomorrow615.compiler.backend.mips.operand.Operand;

import java.util.Collections;
import java.util.List;

public class MipsLi extends MipsInstruction {
    private Operand rd; // 目标寄存器
    private final int imm;         // 立即数

    // 例如: li $t0, 100
    public MipsLi(Operand rd, int imm) {
        this.rd = rd;
        this.imm = imm;
    }

    // Getters
    public Operand getRd() { return rd; }
    public int getImm() { return imm; }

    @Override
    public String toString() {
        return String.format("li %s, %d", rd.toAsm(), imm);
    }

    // ========== Def/Use 接口实现 ==========

    @Override
    public List<Operand> getDef() {
        // li rd, imm -> 定义了 rd
        return List.of(rd);
    }

    @Override
    public List<Operand> getUse() {
        // li 指令只使用立即数，没有寄存器 Use
        return Collections.emptyList();
    }

    @Override
    public void replaceDef(Operand oldOp, Operand newOp) {
        if (rd.equals(oldOp)) {
            rd = newOp;
        }
    }

    @Override
    public void replaceUse(Operand oldOp, Operand newOp) {
        // 无 Use，无需替换
    }
}