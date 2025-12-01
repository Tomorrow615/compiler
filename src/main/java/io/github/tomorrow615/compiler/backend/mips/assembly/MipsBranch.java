package io.github.tomorrow615.compiler.backend.mips.assembly;

import io.github.tomorrow615.compiler.backend.mips.MipsRegister;

public class MipsBranch extends MipsInstruction {
    // 指令类型: beq, bne, j, jal, jr, bnez, beqz
    private final String op;
    private final MipsRegister rs;
    private final MipsRegister rt;
    private final String label;

    // 构造函数 1: 寄存器跳转 (jr $ra)
    public MipsBranch(String op, MipsRegister rs) {
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
    public MipsBranch(String op, MipsRegister rs, MipsRegister rt, String label) {
        this.op = op;
        this.rs = rs;
        this.rt = rt;
        this.label = label;
    }

    // [新增] 构造函数 4: 单寄存器条件跳转 (bnez $t0, label)
    public MipsBranch(String op, MipsRegister rs, String label) {
        this.op = op;
        this.rs = rs;
        this.rt = null;
        this.label = label;
    }

    @Override
    public String toString() {
        if (label != null) {
            if (rs != null && rt != null) {
                // beq $t0, $t1, label
                return String.format("%s %s, %s, %s", op, rs, rt, label);
            } else if (rs != null) {
                // [新增分支] bnez $t0, label
                return String.format("%s %s, %s", op, rs, label);
            } else {
                // j label
                return String.format("%s %s", op, label);
            }
        } else {
            // jr $ra
            return String.format("%s %s", op, rs);
        }
    }
}