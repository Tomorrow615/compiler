package io.github.tomorrow615.compiler.backend.mips.operand;

/**
 * 操作数接口 - 后端寄存器分配的基础抽象
 * 
 * 所有可以作为指令操作数的类型（物理寄存器、虚拟寄存器）都需要实现此接口。
 */
public interface Operand {
    
    /**
     * 生成汇编代码中的字符串表示
     * 例如: "$t0" 或 "%v1"
     */
    String toAsm();
    
    /**
     * 判断是否为物理寄存器
     * @return true 如果是物理寄存器（可直接用于生成汇编），false 如果是虚拟寄存器（需要分配）
     */
    boolean isPhysical();
}
