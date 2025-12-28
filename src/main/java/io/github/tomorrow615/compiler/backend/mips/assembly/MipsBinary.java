package io.github.tomorrow615.compiler.backend.mips.assembly;

import io.github.tomorrow615.compiler.backend.mips.operand.Operand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MIPS 二元/一元算术指令
 * 
 * 支持的格式：
 * - R-Type: add rd, rs, rt
 * - I-Type: addi rd, rs, imm
 * - Special: div rs, rt (无目标寄存器)
 * - Move from: mflo rd, mfhi rd (只有目标寄存器)
 */
public class MipsBinary extends MipsInstruction {
    private final String op;
    private Operand rd;
    private Operand rs;
    private Operand rt;
    private final Integer imm;

    // 构造函数 1: R-Type (add $t0, $t1, $t2)
    public MipsBinary(String op, Operand rd, Operand rs, Operand rt) {
        this.op = op;
        this.rd = rd;
        this.rs = rs;
        this.rt = rt;
        this.imm = null;
    }

    // 构造函数 2: I-Type (addi $t0, $t1, 4)
    public MipsBinary(String op, Operand rd, Operand rs, int imm) {
        this.op = op;
        this.rd = rd;
        this.rs = rs;
        this.rt = null;
        this.imm = imm;
    }

    // 构造函数 3: Special (div $t0, $t1) - 无目标寄存器
    public MipsBinary(String op, Operand rs, Operand rt) {
        this.op = op;
        this.rd = null;
        this.rs = rs;
        this.rt = rt;
        this.imm = null;
    }

    public MipsBinary(String op, Operand rd) {
        this.op = op;
        this.rd = rd;
        this.rs = null;
        this.rt = null;
        this.imm = null;
    }

    // Getters
    public String getOp() { return op; }
    public Operand getRd() { return rd; }
    public Operand getRs() { return rs; }
    public Operand getRt() { return rt; }
    public Integer getImm() { return imm; }

    @Override
    public String toString() {
        if (imm != null) {
            // addi $t0, $t1, 100
            return String.format("%s %s, %s, %d", op, rd.toAsm(), rs.toAsm(), imm);
        } else {
            if (rd != null && rs != null && rt != null) {
                // add $t0, $t1, $t2
                return String.format("%s %s, %s, %s", op, rd.toAsm(), rs.toAsm(), rt.toAsm());
            } else if (rd == null && rs != null && rt != null) {
                // div $t0, $t1
                return String.format("%s %s, %s", op, rs.toAsm(), rt.toAsm());
            } else if (rd != null && rs == null && rt == null) {
                // mflo $t0
                return String.format("%s %s", op, rd.toAsm());
            }
            return op; // fallback
        }
    }

    // ========== Def/Use 接口实现 ==========

    @Override
    public List<Operand> getDef() {
        if (rd != null) {
            return List.of(rd);
        }
        return Collections.emptyList();
    }

    @Override
    public List<Operand> getUse() {
        List<Operand> uses = new ArrayList<>();
        if (rs != null) uses.add(rs);
        if (rt != null) uses.add(rt);
        return uses;
    }

    @Override
    public void replaceDef(Operand oldOp, Operand newOp) {
        if (rd != null && rd.equals(oldOp)) {
            rd = newOp;
        }
    }

    @Override
    public void replaceUse(Operand oldOp, Operand newOp) {
        if (rs != null && rs.equals(oldOp)) {
            rs = newOp;
        }
        if (rt != null && rt.equals(oldOp)) {
            rt = newOp;
        }
    }
}
