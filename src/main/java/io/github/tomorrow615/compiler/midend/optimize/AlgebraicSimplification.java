package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 代数简化优化 Pass
 * 
 * 处理恒等式和零元素：
 * - x + 0 -> x
 * - x - 0 -> x
 * - x * 0 -> 0
 * - x * 1 -> x
 * - x / 1 -> x
 * - x % 1 -> 0
 * - 0 / x -> 0 (x != 0)
 * - 0 % x -> 0 (x != 0)
 */
public class AlgebraicSimplification implements Pass {

    @Override
    public String getName() {
        return "AlgebraicSimplification";
    }

    @Override
    public void runOnFunction(Function function) {
        boolean changed = true;
        
        while (changed) {
            changed = false;
            
            for (BasicBlock bb : function.getBasicBlocks()) {
                List<Instruction> toRemove = new ArrayList<>();
                
                for (Instruction inst : bb.getInstructions()) {
                    // [Fix] 先进行操作数归一化：将常数放到右边
                    if (canonicalizeOperands(inst)) {
                        changed = true;
                    }
                    
                    Value result = trySimplify(inst);
                    if (result != null) {
                        replaceAllUsesWith(inst, result);
                        toRemove.add(inst);
                        changed = true;
                    }
                }
                
                bb.getInstructions().removeAll(toRemove);
            }
        }
    }
    
    /**
     * [Fix] 操作数归一化：对于可交换运算，将常数放到右边
     * 这样 5 * x 变成 x * 5，便于 ArithmeticOptimization 处理
     * @return true 如果进行了交换
     */
    private boolean canonicalizeOperands(Instruction inst) {
        if (!(inst instanceof BinaryOpInst bin)) {
            return false;
        }
        
        BinaryOpInst.OpCode op = bin.getOp();
        // 只处理可交换运算
        if (op != BinaryOpInst.OpCode.ADD && op != BinaryOpInst.OpCode.MUL) {
            return false;
        }
        
        Value lhs = bin.getLhs();
        Value rhs = bin.getRhs();
        
        // 如果左边是常数，右边不是常数，则交换
        if (lhs instanceof ConstantInt && !(rhs instanceof ConstantInt)) {
            bin.setOperand(0, rhs);
            bin.setOperand(1, lhs);
            return true;
        }
        
        return false;
    }


    private Value trySimplify(Instruction inst) {
        if (!(inst instanceof BinaryOpInst bin)) {
            return null;
        }
        
        Value lhs = bin.getLhs();
        Value rhs = bin.getRhs();
        BinaryOpInst.OpCode op = bin.getOp();
        
        // 检查左右操作数是否为常量 0 或 1
        boolean lhsIsZero = isConstantZero(lhs);
        boolean rhsIsZero = isConstantZero(rhs);
        boolean lhsIsOne = isConstantOne(lhs);
        boolean rhsIsOne = isConstantOne(rhs);
        
        return switch (op) {
            case ADD -> {
                // x + 0 -> x
                if (rhsIsZero) yield lhs;
                // 0 + x -> x
                if (lhsIsZero) yield rhs;
                yield null;
            }
            case SUB -> {
                // x - 0 -> x
                if (rhsIsZero) yield lhs;
                yield null;
            }
            case MUL -> {
                // x * 0 -> 0
                if (rhsIsZero) yield new ConstantInt(0);
                // 0 * x -> 0
                if (lhsIsZero) yield new ConstantInt(0);
                // x * 1 -> x
                if (rhsIsOne) yield lhs;
                // 1 * x -> x
                if (lhsIsOne) yield rhs;
                yield null;
            }
            case SDIV -> {
                // x / 1 -> x
                if (rhsIsOne) yield lhs;
                // 0 / x -> 0 (x != 0，假设代码无 UB)
                if (lhsIsZero && !rhsIsZero) yield new ConstantInt(0);
                yield null;
            }
            case SREM -> {
                // x % 1 -> 0
                if (rhsIsOne) yield new ConstantInt(0);
                // 0 % x -> 0 (x != 0)
                if (lhsIsZero && !rhsIsZero) yield new ConstantInt(0);
                yield null;
            }
            default -> null;
        };
    }

    private boolean isConstantZero(Value v) {
        return v instanceof ConstantInt c && c.getValue() == 0;
    }

    private boolean isConstantOne(Value v) {
        return v instanceof ConstantInt c && c.getValue() == 1;
    }

    private void replaceAllUsesWith(Value oldValue, Value newValue) {
        List<Use> usersSnapshot = new ArrayList<>(oldValue.getUsers());
        for (Use use : usersSnapshot) {
            use.setValue(newValue);
        }
    }
}
