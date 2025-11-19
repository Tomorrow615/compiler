package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.*;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;
import io.github.tomorrow615.compiler.midend.llvm.value.Use;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.*;

import java.util.List;
import java.util.stream.Collectors;

public class CallInst extends Instruction {
    public CallInst(Function function, List<Value> args, String name, BasicBlock parentBlock) {
        super(function.getReturnType(), name, parentBlock);
        this.addOperand(function);
        for (Value arg : args) {
            this.addOperand(arg);
        }
    }

    public Function getFunction() {
        return (Function) this.getOperand(0);
    }

    public List<Value> getArguments() {
        return this.operands.subList(1, this.operands.size()).stream()
                .map(Use::getValue)
                .collect(Collectors.toList());
    }

    @Override
    public String toString(SlotTracker tracker) {
        StringBuilder sb = new StringBuilder();
        Function func = getFunction();
        Type retType = func.getReturnType();

        if (retType.isVoidType()) {
            sb.append("call void ");
        } else {
            sb.append(tracker.getName(this)).append(" = call ").append(retType).append(" ");
        }
        sb.append(tracker.getName(func));

        sb.append("(");
        String argsStr = getArguments().stream()
                .map(arg -> arg.getType().toString() + " " + tracker.getName(arg))
                .collect(Collectors.joining(", "));
        sb.append(argsStr).append(")");
        return sb.toString();
    }

    @Override
    public String toString() {
        return "CallInst<" + this.name + ">@" + hashCode();
    }
}
