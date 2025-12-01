package io.github.tomorrow615.compiler.backend.mips.assembly;

import io.github.tomorrow615.compiler.backend.mips.MipsRegister;

/**
 * 比较指令
 * 将结果 (0或1) 存入 rd
 * 支持: seq, sne, slt, sle, sgt, sge
 */
public class MipsCompare extends MipsInstruction {
    private final String op; // seq, sne, ...
    private final MipsRegister rd;
    private final MipsRegister rs;
    private final MipsRegister rt;

    public MipsCompare(String op, MipsRegister rd, MipsRegister rs, MipsRegister rt) {
        this.op = op;
        this.rd = rd;
        this.rs = rs;
        this.rt = rt;
    }

    @Override
    public String toString() {
        // 格式: seq $t0, $t1, $t2
        return String.format("%s %s, %s, %s", op, rd, rs, rt);
    }
}