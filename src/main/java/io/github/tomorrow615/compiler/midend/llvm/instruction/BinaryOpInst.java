package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.*;

public class BinaryOpInst extends Instruction {

    // 定义操作码
    public enum OpCode {
        ADD, // +
        SUB, // -
        MUL, // *
        SDIV, // / (有符号除法)
        SREM, // % (有符号取余)
        AND,  // [新添加]
        OR    // [新添加]
    }

    private final OpCode op;

    public BinaryOpInst(OpCode op, Value lhs, Value rhs, String name, BasicBlock parentBlock) {
        // 结果类型应与操作数类型一致 (SysY中都是 i32)
        super(lhs.getType(), name, parentBlock);
        this.op = op;
        this.addOperand(lhs); // 操作数0: lhs
        this.addOperand(rhs); // 操作数1: rhs
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
            case AND -> "and"; // [新添加]
            case OR -> "or";   // [新添加]
        };

        // e.g., %3 = add i32 %1, %2
        // SysY 规定了 nsw (No Signed Wrap)，为简化，我们先不加
        return tracker.getName(this) + " = " + opStr + " " + this.getType().toString()
                + " " + tracker.getName(getLhs()) + ", " + tracker.getName(getRhs());
    }

    @Override
    public String toString() {
        return "BinaryOpInst<" + op.name() + ", " + this.name + ">@" + hashCode(); // 调试用
    }
}
