package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.ArrayType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.util.SlotTracker;

import java.util.List;
import java.util.stream.Collectors;

public class ConstantArray extends Constant {

    private final List<Constant> elements;

    public ConstantArray(Type type, List<Constant> elements) {
        super(type); // Constant 的构造函数
        this.elements = elements;
    }

    public List<Constant> getElements() {
        return elements;
    }

    @Override
    public String toString(SlotTracker tracker) {
        // [cite: 980, 977] SysY 规定未赋值的部分编译器需要将其置0
        // 如果我们提供的 elements 是空的，或者比数组大小少
        // 我们这里简化处理：如果 elements 列表是空的，就认为是 zeroinitializer
        if (elements.isEmpty()) {
            return "zeroinitializer";
        }

        // 否则，打印 [i32 1, i32 2, i32 3]
        String elementsStr = elements.stream()
                .map(e -> e.getType().toString() + " " + e.toString(tracker))
                .collect(Collectors.joining(", "));

        return "[" + elementsStr + "]";
    }

    @Override
    public String toString() {
        return "ConstantArray<" + this.getType().toString() + ">@" + hashCode();
    }
}
