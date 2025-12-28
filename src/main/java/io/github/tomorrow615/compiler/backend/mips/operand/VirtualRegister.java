package io.github.tomorrow615.compiler.backend.mips.operand;

import java.util.Objects;

/**
 * 虚拟寄存器 - 用于寄存器分配前的中间表示
 * 
 * 每个 VirtualRegister 都有一个唯一的 ID，用于在冲突图中标识。
 * 在寄存器分配完成后，会被替换为物理寄存器 (MipsRegister)。
 */
public class VirtualRegister implements Operand {
    
    private static int counter = 0;
    
    private final int id;
    
    public VirtualRegister() {
        this.id = counter++;
    }
    
    public int getId() {
        return id;
    }
    
    /**
     * 重置计数器（用于测试或新函数开始时）
     */
    public static void resetCounter() {
        counter = 0;
    }
    
    @Override
    public String toAsm() {
        // 虚拟寄存器不能直接生成汇编，调用此方法说明分配器有 bug
        throw new IllegalStateException("VirtualRegister %v" + id + " was not allocated!");
    }
    
    @Override
    public boolean isPhysical() {
        return false;
    }
    
    @Override
    public String toString() {
        return "%v" + id;  // 用于调试输出
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VirtualRegister that)) return false;
        return id == that.id;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
