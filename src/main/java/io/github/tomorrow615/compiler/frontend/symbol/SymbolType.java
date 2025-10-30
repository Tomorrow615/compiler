package io.github.tomorrow615.compiler.frontend.symbol;

public enum SymbolType {
    ConstInt("ConstInt"),
    ConstIntArray("ConstIntArray"),
    Int("Int"),
    IntArray("IntArray"),
    StaticInt("StaticInt"),
    StaticIntArray("StaticIntArray"),
    VoidFunc("VoidFunc"),
    IntFunc("IntFunc");

    private final String outputName;

    SymbolType(String outputName) {
        this.outputName = outputName;
    }

    @Override
    public String toString() {
        return this.outputName;
    }
}
