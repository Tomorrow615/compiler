package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.SlotTracker;

import java.util.stream.Collectors;

public class PhiInst extends Instruction {

    /**
     * @param type PHI 节点的结果类型 (e.g., i1)
     * @param name 结果名字 (e.g., %lor.phi)
     * @param parentBlock 插入到的基本块 (e.g., mergeBB)
     */
    public PhiInst(Type type, String name, BasicBlock parentBlock) {
        super(type, name, parentBlock);
    }

    /**
     * PHI 节点的核心：添加一个 "来自" 的路径
     * @param value 来自该路径的值 (e.g., i1 1)
     * @param block 来自该路径的基本块 (e.g., %lhs.end)
     */
    public void addIncoming(Value value, BasicBlock block) {
        // 我们将 value 和 block 成对添加到操作数中
        this.addOperand(value);
        this.addOperand(block);
    }

    @Override
    public String toString(SlotTracker tracker) {
        // e.g., %5 = phi i1 [ true, %3 ], [ %4, %2 ]
        StringBuilder sb = new StringBuilder();
        sb.append(tracker.getName(this)).append(" = phi ").append(this.getType().toString());

        String incomings = "";
        for (int i = 0; i < this.operands.size(); i += 2) {
            Value val = this.getOperand(i);
            Value block = this.getOperand(i + 1);
            if (i > 0) sb.append(",");
            sb.append(" [ ").append(tracker.getName(val)).append(", ").append(tracker.getName(block)).append(" ]");
        }

        sb.append(incomings);
        return sb.toString();
    }

    @Override
    public String toString() {
        return "PhiInst<" + this.name + ">@" + hashCode();
    }
}