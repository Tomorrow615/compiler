package io.github.tomorrow615.compiler.frontend.symbol;

public class ValueSymbol extends Symbol {

    private final int dimension;

    /**
     * 构造一个值符号。
     *
     * @param name      符号名称
     * @param type      符号类型 (必须是 ConstInt, ConstIntArray, Int, IntArray, StaticInt, StaticIntArray 之一)
     * @param line      定义所在的行号
     * @param dimension 维度 (0 或 1)
     */
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
