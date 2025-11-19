package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.IntegerType;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.*;

public class IcmpInst extends Instruction {
    public enum CmpType {
        EQ,  // == (equal)
        NE,  // != (not equal)
        SGT, // >  (signed greater than)
        SGE, // >= (signed greater or equal)
        SLT, // <  (signed less than)
        SLE  // <= (signed less or equal)
    }

    private final CmpType cmpType;

    public IcmpInst(CmpType type, Value lhs, Value rhs, String name, BasicBlock parentBlock) {
        super(IntegerType.i1, name, parentBlock);
        this.cmpType = type;
        this.addOperand(lhs); // 操作数0: lhs
        this.addOperand(rhs); // 操作数1: rhs
    }

    public CmpType getCmpType() {
        return cmpType;
    }

    public Value getLhs() {
        return this.getOperand(0);
    }

    public Value getRhs() {
        return this.getOperand(1);
    }

    @Override
    public String toString(SlotTracker tracker) {
        String typeStr = switch (cmpType) {
            case EQ -> "eq";
            case NE -> "ne";
            case SGT -> "sgt";
            case SGE -> "sge";
            case SLT -> "slt";
            case SLE -> "sle";
        };
        return tracker.getName(this) + " = icmp " + typeStr + " " + getLhs().getType().toString()
                + " " + tracker.getName(getLhs()) + ", " + tracker.getName(getRhs());
    }

    @Override
    public String toString() {
        return "IcmpInst<" + cmpType.name() + ", " + this.name + ">@" + hashCode();
    }
}
