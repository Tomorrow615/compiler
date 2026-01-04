package io.github.tomorrow615.compiler.backend.mips.assembly;

import io.github.tomorrow615.compiler.backend.mips.operand.Operand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MipsBranch extends MipsInstruction {
    // 指令类型: beq, bne, j, jal, jr, bnez, beqz
    private final String op;
    private Operand rs;
    private Operand rt;
    private final String label;

    // 构造函数 1: 寄存器跳转 (jr $ra)
    public MipsBranch(String op, Operand rs) {
        this.op = op;
        this.rs = rs;
        this.rt = null;
        this.label = null;
    }

    // 构造函数 2: 直接跳转 (j label, jal label)
    public MipsBranch(String op, String label) {
        this.op = op;
        this.rs = null;
        this.rt = null;
        this.label = label;
    }

    // 构造函数 3: 双寄存器条件跳转 (beq $t0, $t1, label)
    public MipsBranch(String op, Operand rs, Operand rt, String label) {
        this.op = op;
        this.rs = rs;
        this.rt = rt;
        this.label = label;
    }

    // [新增] 构造函数 4: 单寄存器条件跳转 (bnez $t0, label)
    public MipsBranch(String op, Operand rs, String label) {
        this.op = op;
        this.rs = rs;
        this.rt = null;
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getOp() {
        return op;
    }

    public Operand getRs() {
        return rs;
    }

    public Operand getRt() {
        return rt;
    }

    @Override
    public String toString() {
        if (label != null) {
            if (rs != null && rt != null) {
                // beq $t0, $t1, label
                return String.format("%s %s, %s, %s", op, rs.toAsm(), rt.toAsm(), label);
            } else if (rs != null) {
                // [新增分支] bnez $t0, label
                return String.format("%s %s, %s", op, rs.toAsm(), label);
            } else {
                // j label
                return String.format("%s %s", op, label);
            }
        } else {
            // jr $ra
            return String.format("%s %s", op, rs.toAsm());
        }
    }

    // ========== Def/Use 接口实现 ==========

    // Caller-Saved Registers (clobbered by jal)
    private static final List<Operand> CALL_DEFS = new ArrayList<>();
    static {
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.RA);
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.V0);
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.V1);
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.A0);
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.A1);
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.A2);
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.A3);
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.T0);
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.T1);
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.T2);
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.T3);
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.T4);
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.T5);
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.T6);
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.T7);
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.T8);
        CALL_DEFS.add(io.github.tomorrow615.compiler.backend.mips.MipsRegister.T9);
    }

    @Override
    public List<Operand> getDef() {
        if (op.equals("jal") || op.equals("jalr")) {
            return CALL_DEFS;
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
        // 无 Def
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