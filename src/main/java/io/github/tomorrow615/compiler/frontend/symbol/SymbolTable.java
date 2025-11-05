package io.github.tomorrow615.compiler.frontend.symbol;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class SymbolTable {
    private final SymbolTable parent;
    private final int scopeId; // 当前作用域的ID
    private final Map<String, Symbol> symbols = new HashMap<>();
    private final List<Symbol> orderedSymbols = new ArrayList<>();

    public SymbolTable(SymbolTable parent, int scopeId) {
        this.parent = parent;
        this.scopeId = scopeId;
    }

    public boolean addSymbol(Symbol symbol) {
        String name = symbol.getName();
        if (symbols.containsKey(name)) {
            return false;
        }

        symbols.put(name, symbol);
        orderedSymbols.add(symbol);
        return true;
    }

    // 这个方法用于添加内置函数，如 getint 和 printf
    public void addBuiltInSymbol(Symbol symbol) {
        String name = symbol.getName();
        // 假设内置函数不会重定义
        if (!symbols.containsKey(name)) {
            symbols.put(name, symbol);
        }
    }

    public Symbol lookup(String name) {
        Symbol symbol = symbols.get(name);
        if (symbol != null) {
            return symbol;
        }
        if (parent != null) {
            return parent.lookup(name);
        }
        return null;
    }

    public SymbolTable getParent() {
        return parent;
    }

    public int getScopeId() {
        return scopeId;
    }

    public List<Symbol> getOrderedSymbols() {
        return Collections.unmodifiableList(orderedSymbols);
    }
}