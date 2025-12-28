package io.github.tomorrow615.compiler.backend.mips.assembly;

import io.github.tomorrow615.compiler.backend.mips.operand.Operand;

import java.util.ArrayList;
import java.util.List;

/**
 * 比较指令
 * 将结果 (0或1) 存入 rd
 * 支持: seq, sne, slt, sle, sgt, sge
 */
public class MipsCompare extends MipsInstruction {
    private final String op; // seq, sne, ...
    private Operand rd;
    private Operand rs;
    private Operand rt;

    public MipsCompare(String op, Operand rd, Operand rs, Operand rt) {
        this.op = op;
        this.rd = rd;
        this.rs = rs;
        this.rt = rt;
    }

    @Override
    public String toString() {
        // 格式: seq $t0, $t1, $t2
        return String.format("%s %s, %s, %s", op, rd.toAsm(), rs.toAsm(), rt.toAsm());
    }

    // ========== Def/Use 接口实现 ==========

    @Override
    public List<Operand> getDef() {
        // slt rd, rs, rt -> 定义了 rd
        return List.of(rd);
    }

    @Override
    public List<Operand> getUse() {
        List<Operand> uses = new ArrayList<>();
        uses.add(rs);
        uses.add(rt);
        return uses;
    }

    @Override
    public void replaceDef(Operand oldOp, Operand newOp) {
        if (rd.equals(oldOp)) {
            rd = newOp;
        }
    }

    @Override
    public void replaceUse(Operand oldOp, Operand newOp) {
        if (rs.equals(oldOp)) {
            rs = newOp;
        }
        if (rt.equals(oldOp)) {
            rt = newOp;
        }
    }
}