package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.util.SlotTracker;

import java.util.List;
import java.util.stream.Collectors;

public class ConstantArray extends Constant {
    private final List<Constant> elements;

    public ConstantArray(Type type, List<Constant> elements) {
        super(type);
        this.elements = elements;
    }

    public List<Constant> getElements() {
        return elements;
    }

    @Override
    public String toString(SlotTracker tracker) {
        if (elements.isEmpty()) {
            return "zeroinitializer";
        }
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
