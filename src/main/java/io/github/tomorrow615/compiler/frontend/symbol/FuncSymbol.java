package io.github.tomorrow615.compiler.frontend.symbol;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class FuncSymbol extends Symbol {
    private final List<ValueSymbol> parameters = new ArrayList<>();

    public FuncSymbol(String name, SymbolType type, int line) {
        super(name, type, line);
    }

    public void addParameter(ValueSymbol param) {
        this.parameters.add(param);
    }

    public List<ValueSymbol> getParameters() {
        return Collections.unmodifiableList(this.parameters);
    }

    public SymbolType getReturnType() {
        return this.type;
    }
}