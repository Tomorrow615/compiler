package io.github.tomorrow615.compiler.backend.mips.assembly;

import io.github.tomorrow615.compiler.backend.mips.operand.Operand;

import java.util.Collections;
import java.util.List;

public class MipsLa extends MipsInstruction {
    private Operand rd;
    private final String label;

    public MipsLa(Operand rd, String label) {
        this.rd = rd;
        this.label = label;
    }

    @Override
    public String toString() {
        return String.format("la %s, %s", rd.toAsm(), label);
    }

    // ========== Def/Use 接口实现 ==========

    @Override
    public List<Operand> getDef() {
        // la rd, label -> 定义了 rd
        return List.of(rd);
    }

    @Override
    public List<Operand> getUse() {
        // la 指令没有寄存器 Use
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
        // 无 Use
    }
}