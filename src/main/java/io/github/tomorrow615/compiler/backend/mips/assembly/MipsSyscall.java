package io.github.tomorrow615.compiler.backend.mips.assembly;

import io.github.tomorrow615.compiler.backend.mips.operand.Operand;

import java.util.Collections;
import java.util.List;

public class MipsSyscall extends MipsInstruction {
    public MipsSyscall() {
    }

    @Override
    public String toString() {
        return "syscall";
    }

    // ========== Def/Use 接口实现 ==========

    @Override
    public List<Operand> getDef() {
        // syscall 会修改 $v0 (返回值), 但在简单分配器中可能忽略
        return Collections.emptyList();
    }

    @Override
    public List<Operand> getUse() {
        // syscall 使用 $v0, $a0...
        return Collections.emptyList();
    }

    @Override
    public void replaceDef(Operand oldOp, Operand newOp) {
        // 无显式操作数
    }

    @Override
    public void replaceUse(Operand oldOp, Operand newOp) {
        // 无显式操作数
    }
}