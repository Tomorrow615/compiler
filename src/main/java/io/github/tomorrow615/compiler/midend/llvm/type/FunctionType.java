package io.github.tomorrow615.compiler.midend.llvm.type;

import java.util.List;
import java.util.stream.Collectors;

public class FunctionType extends Type {
    private final Type returnType;
    private final List<Type> paramTypes;

    public FunctionType(Type returnType, List<Type> paramTypes) {
        this.returnType = returnType;
        this.paramTypes = paramTypes;
    }

    public Type getReturnType() {
        return returnType;
    }

    public List<Type> getParamTypes() {
        return paramTypes;
    }

    @Override
    public boolean isFunctionType() {
        return true;
    }

    @Override
    public String toString() {
        String params = paramTypes.stream()
                .map(Type::toString)
                .collect(Collectors.joining(", "));
        return returnType.toString() + " (" + params + ")";
    }
}
