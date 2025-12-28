package io.github.tomorrow615.compiler.backend.mips;

import io.github.tomorrow615.compiler.backend.mips.operand.Operand;

/**
 * MIPS 32个物理寄存器枚举
 * 包含编号和汇编名称
 */
public enum MipsRegister implements Operand {
    // 常量 0
    ZERO("$zero", 0),
    // 汇编器保留
    AT("$at", 1),
    // 返回值
    V0("$v0", 2), V1("$v1", 3),
    // 参数
    A0("$a0", 4), A1("$a1", 5), A2("$a2", 6), A3("$a3", 7),
    // 临时寄存器 (调用者保存)
    T0("$t0", 8), T1("$t1", 9), T2("$t2", 10), T3("$t3", 11),
    T4("$t4", 12), T5("$t5", 13), T6("$t6", 14), T7("$t7", 15),
    // 全局寄存器 (被调用者保存)
    S0("$s0", 16), S1("$s1", 17), S2("$s2", 18), S3("$s3", 19),
    S4("$s4", 20), S5("$s5", 21), S6("$s6", 22), S7("$s7", 23),
    // 临时寄存器 (续)
    T8("$t8", 24), T9("$t9", 25),
    // 内核保留
    K0("$k0", 26), K1("$k1", 27),
    // 全局指针
    GP("$gp", 28),
    // 栈指针
    SP("$sp", 29),
    // 帧指针
    FP("$fp", 30),
    // 返回地址
    RA("$ra", 31);

    private final String name;
    private final int id;

    MipsRegister(String name, int id) {
        this.name = name;
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return name;
    }
    
    // ========== Operand 接口实现 ==========
    
    @Override
    public String toAsm() {
        return name;
    }
    
    @Override
    public boolean isPhysical() {
        return true;
    }
}
