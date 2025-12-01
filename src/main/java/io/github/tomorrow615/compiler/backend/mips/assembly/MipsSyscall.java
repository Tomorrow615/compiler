package io.github.tomorrow615.compiler.backend.mips.assembly;

public class MipsSyscall extends MipsInstruction {
    public MipsSyscall() {
    }

    @Override
    public String toString() {
        return "syscall";
    }
}