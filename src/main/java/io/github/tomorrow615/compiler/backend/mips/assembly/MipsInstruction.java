package io.github.tomorrow615.compiler.backend.mips.assembly;

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
}