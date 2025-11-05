package io.github.tomorrow615.compiler.frontend.symbol;

public class ValueSymbol extends Symbol {
    private final int dimension;

    public ValueSymbol(String name, SymbolType type, int line, int dimension) {
        super(name, type, line);
        this.dimension = dimension;
    }

    public int getDimension() {
        return dimension;
    }

    public boolean isConst() {
        return type == SymbolType.ConstInt || type == SymbolType.ConstIntArray;
    }
}
