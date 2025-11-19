package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.SlotTracker;

public class PhiInst extends Instruction {
    public PhiInst(Type type, String name, BasicBlock parentBlock) {
        super(type, name, parentBlock);
    }

    public void addIncoming(Value value, BasicBlock block) {
        this.addOperand(value);
        this.addOperand(block);
    }

    @Override
    public String toString(SlotTracker tracker) {
        StringBuilder sb = new StringBuilder();
        sb.append(tracker.getName(this)).append(" = phi ").append(this.getType().toString());
        for (int i = 0; i < this.operands.size(); i += 2) {
            Value val = this.getOperand(i);
            Value block = this.getOperand(i + 1);
            if (i > 0) sb.append(",");
            sb.append(" [ ").append(tracker.getName(val)).append(", %").append(tracker.getName(block)).append(" ]");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "PhiInst<" + this.name + ">@" + hashCode();
    }
}