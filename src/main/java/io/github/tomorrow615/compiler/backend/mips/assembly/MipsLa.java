package io.github.tomorrow615.compiler.backend.mips.assembly;

import io.github.tomorrow615.compiler.backend.mips.MipsRegister;

public class MipsLa extends MipsInstruction {
    private final MipsRegister rd;
    private final String label;

    public MipsLa(MipsRegister rd, String label) {
        this.rd = rd;
        this.label = label;
    }

    @Override
    public String toString() {
        return String.format("la %s, %s", rd, label);
    }
}