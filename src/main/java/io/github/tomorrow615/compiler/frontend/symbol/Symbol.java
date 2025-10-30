package io.github.tomorrow615.compiler.frontend.symbol;

public abstract class Symbol {
    protected final String name;

    protected final SymbolType type;

    protected final int line;

    public Symbol(String name, SymbolType type, int line) {
        this.name = name;
        this.type = type;
        this.line = line;
    }

    public String getName() {
        return name;
    }

    public SymbolType getType() {
        return type;
    }

    public int getLine() {
        return line;
    }
}
