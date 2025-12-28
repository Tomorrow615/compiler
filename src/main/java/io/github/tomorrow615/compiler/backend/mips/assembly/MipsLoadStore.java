package io.github.tomorrow615.compiler.backend.mips.assembly;

import io.github.tomorrow615.compiler.backend.mips.operand.Operand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MipsLoadStore extends MipsInstruction {
    public enum Type {
        LW, // Load Word
        SW  // Store Word
    }

    private final Type type;
    private Operand rt;   // 要加载/存储的目标寄存器
    private Operand base; // 基地址寄存器 (通常是 $sp 或 $fp)
    private final int offset;        // 偏移量

    // 例如: lw $t0, 4($sp)
    public MipsLoadStore(Type type, Operand rt, Operand base, int offset) {
        this.type = type;
        this.rt = rt;
        this.base = base;
        this.offset = offset;
    }

    // Getters
    public Type getType() { return type; }
    public Operand getRt() { return rt; }
    public Operand getBase() { return base; }
    public int getOffset() { return offset; }

    @Override
    public String toString() {
        // 输出格式: lw $t0, 4($sp)
        return String.format("%s %s, %d(%s)", type.name().toLowerCase(), rt.toAsm(), offset, base.toAsm());
    }

    // ========== Def/Use 接口实现 ==========

    @Override
    public List<Operand> getDef() {
        if (type == Type.LW) {
            // lw $t0, 4($sp) -> 定义了 $t0
            return List.of(rt);
        } else {
            // sw $t0, 4($sp) -> 内存写入，不算寄存器定义
            return Collections.emptyList();
        }
    }

    @Override
    public List<Operand> getUse() {
        List<Operand> uses = new ArrayList<>();
        // base 寄存器总是被使用 (用于计算地址)
        uses.add(base);
        
        if (type == Type.SW) {
            // sw $t0, 4($sp) -> 使用了 $t0 (作为值)
            uses.add(rt);
        }
        return uses;
    }

    @Override
    public void replaceDef(Operand oldOp, Operand newOp) {
        if (type == Type.LW) {
            if (rt.equals(oldOp)) {
                rt = newOp;
            }
        }
    }

    @Override
    public void replaceUse(Operand oldOp, Operand newOp) {
        if (base.equals(oldOp)) {
            base = newOp;
        }
        if (type == Type.SW) {
            if (rt.equals(oldOp)) {
                rt = newOp;
            }
        }
    }
}