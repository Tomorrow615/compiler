package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.*;
import io.github.tomorrow615.compiler.midend.llvm.type.*;

import java.util.List;
import java.util.stream.Collectors;

public class GetElementPtrInst extends Instruction {
    private final Type baseType;

    public GetElementPtrInst(Value basePtr, List<Value> indices, String name, BasicBlock parentBlock) {
        super(calculateReturnType(basePtr, indices), name, parentBlock);
        this.baseType = ((PointerType) basePtr.getType()).getTargetType();
        this.addOperand(basePtr);
        for (Value index : indices) {
            this.addOperand(index);
        }
    }

    private static Type calculateReturnType(Value basePtr, List<Value> indices) {
        Type baseType = ((PointerType) basePtr.getType()).getTargetType();
        if (baseType instanceof ArrayType && indices.size() > 1) {
            return new PointerType(((ArrayType) baseType).getElementType());
        } else {
            return new PointerType(baseType);
        }
    }

    public Value getBasePtr() {
        return this.getOperand(0);
    }

    public List<Value> getIndices() {
        return this.operands.subList(1, this.operands.size()).stream()
                .map(use -> use.getValue())
                .collect(Collectors.toList());
    }

    @Override
    public String toString(SlotTracker tracker) {
        StringBuilder sb = new StringBuilder();
        sb.append(tracker.getName(this)).append(" = getelementptr ");
        sb.append(this.baseType.toString()).append(", ");
        sb.append(getBasePtr().getType().toString()).append(" ").append(tracker.getName(getBasePtr()));
        for (Value index : getIndices()) {
            sb.append(", ").append(index.getType().toString()).append(" ").append(tracker.getName(index));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "GetElementPtrInst<" + this.name + ">@" + hashCode();
    }
}
