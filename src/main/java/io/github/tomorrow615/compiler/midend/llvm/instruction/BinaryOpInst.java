package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.*;

public class BinaryOpInst extends Instruction {
    public enum OpCode {
        ADD, // +
        SUB, // -
        MUL, // *
        SDIV, // / (有符号除法)
        SREM, // % (有符号取余)
        AND,
        OR,
        SHL,  // << (左移)
        ASHR,  // >> (算术右移，保留符号位)

    }

    private final OpCode op;

    public BinaryOpInst(OpCode op, Value lhs, Value rhs, String name, BasicBlock parentBlock) {
        super(lhs.getType(), name, parentBlock);
        this.op = op;
        this.addOperand(lhs);
        this.addOperand(rhs);
    }

    public OpCode getOp() {
        return op;
    }

    public Value getLhs() {
        return this.getOperand(0);
    }

    public Value getRhs() {
        return this.getOperand(1);
    }

    @Override
    public String toString(SlotTracker tracker) {
        String opStr = switch (op) {
            case ADD -> "add";
            case SUB -> "sub";
            case MUL -> "mul";
            case SDIV -> "sdiv";
            case SREM -> "srem";
            case AND -> "and";
            case OR -> "or";
            case SHL -> "shl";
            case ASHR -> "ashr";
        };
        return tracker.getName(this) + " = " + opStr + " " + this.getType().toString()
                + " " + tracker.getName(getLhs()) + ", " + tracker.getName(getRhs());
    }

    @Override
    public String toString() {
        return "BinaryOpInst<" + op.name() + ", " + this.name + ">@" + hashCode();
    }
}
