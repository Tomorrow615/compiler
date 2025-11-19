package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.FunctionType;
import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Function extends GlobalValue {
    private final List<BasicBlock> basicBlocks;
    private final List<Argument> arguments;
    private final FunctionType functionType;

    public Function(FunctionType type, String name) {
        super(new PointerType(type), name);
        this.functionType = type;
        this.basicBlocks = new ArrayList<>();
        this.arguments = new ArrayList<>();

        int i = 0;
        for (Type paramType : type.getParamTypes()) {
            this.arguments.add(new Argument(paramType, "%" + i, this, i++));
        }
    }

    public List<BasicBlock> getBasicBlocks() {
        return basicBlocks;
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    public FunctionType getFunctionType() {
        return functionType;
    }

    public Type getReturnType() {
        return this.functionType.getReturnType();
    }

    public void addBasicBlock(BasicBlock bb) {
        this.basicBlocks.add(bb);
    }

    public boolean isDeclaration() {
        return this.basicBlocks.isEmpty();
    }

    @Override
    public String toString(SlotTracker tracker) {
        StringBuilder sb = new StringBuilder();
        if (isDeclaration()) {
            sb.append("declare ");
        } else {
            sb.append("define dso_local ");
        }
        sb.append(this.getReturnType().toString()).append(" ");
        sb.append(tracker.getName(this));
        sb.append("(");
        String params = this.arguments.stream()
                .map(arg -> arg.getType().toString() + " " + tracker.getName(arg))
                .collect(Collectors.joining(", "));
        sb.append(params).append(")");
        if (isDeclaration()) {
            return sb.toString();
        }
        sb.append(" {\n");
        for (BasicBlock bb : basicBlocks) {
            sb.append(bb.toString(tracker));
        }
        sb.append("}\n");
        return sb.toString();
    }

    @Override
    public String toString() {
        return "Function<" + this.name + ">@" + hashCode(); // 调试用
    }
}
