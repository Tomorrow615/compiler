package io.github.tomorrow615.compiler.backend.mips.assembly;

import io.github.tomorrow615.compiler.backend.mips.operand.Operand;
import java.util.List;

/**
 * MIPS 汇编指令的抽象基类
 * 所有具体指令 (Add, Lw, J...) 都继承自此类
 */
public abstract class MipsInstruction {
    // 用于构建双向链表或便于插入（可选，目前先留白，后续需要可添加 prev/next）

    /**
     * 生成该指令对应的汇编字符串
     * 例如: "add $t0, $t1, $t2"
     */
    @Override
    public abstract String toString();
    
    // ========== Def/Use 接口 (用于寄存器分配) ==========
    
    /**
     * 获取该指令定义（写入）的操作数列表
     * 例如: add $t0, $t1, $t2 -> [$t0]
     */
    public abstract List<Operand> getDef();
    
    /**
     * 获取该指令使用（读取）的操作数列表
     * 例如: add $t0, $t1, $t2 -> [$t1, $t2]
     */
    public abstract List<Operand> getUse();
    
    /**
     * 替换定义操作数
     * @param oldOp 旧操作数
     * @param newOp 新操作数
     */
    public abstract void replaceDef(Operand oldOp, Operand newOp);
    
    /**
     * 替换使用操作数
     * @param oldOp 旧操作数
     * @param newOp 新操作数
     */
    public abstract void replaceUse(Operand oldOp, Operand newOp);
}
