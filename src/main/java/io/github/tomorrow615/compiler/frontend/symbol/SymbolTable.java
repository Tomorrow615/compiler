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

    /**
     * 构造一个新的作用域。
     * @param parent  父作用域 (null 表示为全局作用域)
     * @param scopeId 作用域ID
     */
    public SymbolTable(SymbolTable parent, int scopeId) {
        this.parent = parent;
        this.scopeId = scopeId;
    }

    public boolean addSymbol(Symbol symbol) {
        String name = symbol.getName();
        // 只检查当前作用域是否重定义
        if (symbols.containsKey(name)) {
            // 名字重定义，返回 false，由上层调用者 (SemanticVisitor) 报告错误
            return false;
        }

        // 添加到两个表中
        symbols.put(name, symbol);
        orderedSymbols.add(symbol);
        return true;
    }

    // 这个方法用于添加内置函数，如 getint 和 printf
    // 它们需要被查找到 (添加到 'symbols')
    // 但不应该被输出到 symbol.txt (不添加到 'orderedSymbols')
    public void addBuiltInSymbol(Symbol symbol) {
        String name = symbol.getName();
        // 假设内置函数不会重定义
        if (!symbols.containsKey(name)) {
            symbols.put(name, symbol);
        }
        // 注意：我们 *不* 调用 orderedSymbols.add(symbol);
    }

    public Symbol lookup(String name) {
        // 1. 先在当前作用域查找
        Symbol symbol = symbols.get(name);
        if (symbol != null) {
            return symbol;
        }

        // 2. 如果当前找不到，并且存在父作用域，则去父作用域查找
        if (parent != null) {
            return parent.lookup(name);
        }

        // 3. 已经到了全局作用域 (parent == null) 且没找到，返回 null
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