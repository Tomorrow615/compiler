package io.github.tomorrow615.compiler.frontend.symbol;

import io.github.tomorrow615.compiler.midend.llvm.value.Value;

public class ValueSymbol extends Symbol {
    private final int dimension;
    private Value llvmValue;

    public ValueSymbol(String name, SymbolType type, int line, int dimension) {
        super(name, type, line);
        this.dimension = dimension;
        this.llvmValue = null;
    }

    public int getDimension() {
        return dimension;
    }

    public boolean isConst() {
        return type == SymbolType.ConstInt || type == SymbolType.ConstIntArray;
    }

    public void setLlvmValue(Value llvmValue) {
        this.llvmValue = llvmValue;
    }

    public Value getLlvmValue() {
        return llvmValue;
    }
}
