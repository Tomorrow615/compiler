package io.github.tomorrow615.compiler.backend.mips.assembly;

import io.github.tomorrow615.compiler.backend.mips.MipsRegister;

public class MipsBinary extends MipsInstruction {
    private final String op;
    private final MipsRegister rd;
    private final MipsRegister rs;
    private final MipsRegister rt;
    private final Integer imm;

    // 构造函数 1: R-Type (add $t0, $t1, $t2)
    public MipsBinary(String op, MipsRegister rd, MipsRegister rs, MipsRegister rt) {
        this.op = op;
        this.rd = rd;
        this.rs = rs;
        this.rt = rt;
        this.imm = null;
    }

    // 构造函数 2: I-Type (addi $t0, $t1, 4)
    public MipsBinary(String op, MipsRegister rd, MipsRegister rs, int imm) {
        this.op = op;
        this.rd = rd;
        this.rs = rs;
        this.rt = null;
        this.imm = imm;
    }

    // [新增] 构造函数 3: Special (div $t0, $t1) - 无目标寄存器
    public MipsBinary(String op, MipsRegister rs, MipsRegister rt) {
        this.op = op;
        this.rd = null; // 无 rd
        this.rs = rs;
        this.rt = rt;
        this.imm = null;
    }

    // [新增] 构造函数 4: Special (mflo $t0) - 只有目标寄存器
    // 为了区分单寄存器操作是读还是写，我们约定 rd 传入 null 时表示 rs 是源，反之亦然。
    // 但为了简单，mflo 的语义是写入 rd。这里我们就用 rd 字段。
    public MipsBinary(String op, MipsRegister rd) {
        this.op = op;
        this.rd = rd;
        this.rs = null;
        this.rt = null;
        this.imm = null;
    }

    @Override
    public String toString() {
        if (imm != null) {
            // addi $t0, $t1, 100
            return String.format("%s %s, %s, %d", op, rd, rs, imm);
        } else {
            if (rd != null && rs != null && rt != null) {
                // add $t0, $t1, $t2
                return String.format("%s %s, %s, %s", op, rd, rs, rt);
            } else if (rd == null && rs != null && rt != null) {
                // [新增] div $t0, $t1
                return String.format("%s %s, %s", op, rs, rt);
            } else if (rd != null && rs == null && rt == null) {
                // [新增] mflo $t0
                return String.format("%s %s", op, rd);
            }
            return op; // fallback
        }
    }
}