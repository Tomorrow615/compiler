package io.github.tomorrow615.compiler.frontend.symbol;

import io.github.tomorrow615.compiler.midend.llvm.value.Value;

import java.util.List;

public class ValueSymbol extends Symbol {
    private final int dimension;
    private Value llvmValue;
    private Integer constValue = null;
    private int arraySize = 0;
    private List<Integer> constArrayValues = null;

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

    public void setConstValue(int value) {
        this.constValue = value;
    }

    public Integer getConstValue() {
        return this.constValue;
    }

    public void setArraySize(int size) {
        this.arraySize = size;
    }

    public int getArraySize() {
        return this.arraySize;
    }

    public void setConstArrayValues(List<Integer> vals) { this.constArrayValues = vals; }

    public List<Integer> getConstArrayValues() { return constArrayValues; }

    public Integer getConstArrayValue(int index) {
        if (constArrayValues != null && index >= 0 && index < constArrayValues.size()) {
            return constArrayValues.get(index);
        }
        return null;
    }
}
