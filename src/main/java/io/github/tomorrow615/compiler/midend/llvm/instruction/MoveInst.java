package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.SlotTracker;

/**
 * Move 指令 - 用于 Phi 消除后的赋值
 * dest = move src
 */
public class MoveInst extends Instruction {
    
    public MoveInst(Type type, String name, Value src, BasicBlock parent) {
        super(type, name, parent);
        this.addOperand(src);
    }
    
    public Value getSource() {
        return this.getOperand(0);
    }
    
    @Override
    public String toString(SlotTracker tracker) {
        return tracker.getName(this) + " = move " + 
               this.getType() + " " + 
               tracker.getValueName(getSource());
    }
}
