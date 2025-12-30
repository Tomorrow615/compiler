package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 代数简化优化 Pass (Enhanced)
 * 
 * 处理恒等式、零元素和自运算消除：
 * 
 * [算术运算]
 * - x + 0 -> x
 * - x - 0 -> x
 * - x - x -> 0 (non-constant)
 * - x * 0 -> 0
 * - x * 1 -> x
 * - x / 1 -> x
 * - x / x -> 1
 * - 0 / x -> 0
 * - x % 1 -> 0
 * - x % x -> 0
 * - 0 % x -> 0
 * 
 * [位运算]
 * - x & 0  -> 0
 * - x & -1 -> x (all ones)
 * - x & x  -> x
 * - x | 0  -> x
 * - x | -1 -> -1
 * - x | x  -> x

 * 
 * [比较运算]
 * - x == x -> true
 * - x != x -> false
 * - x < x  -> false
 * ...
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
                // 使用快照副本迭代，防止并发修改异常
                List<Instruction> instructions = new ArrayList<>(bb.getInstructions());
                List<Instruction> toRemove = new ArrayList<>();
                
                for (Instruction inst : instructions) {
                    // [Fix] 先进行操作数归一化：将常数放到右边
                    if (canonicalizeOperands(inst)) {
                        changed = true;
                    }
                    
                    Value result = trySimplify(inst);
                    
                    // 特殊处理 ICMP
                    if (result == null && inst instanceof IcmpInst icmp) {
                        result = trySimplifyIcmp(icmp);
                    }
                    
                    if (result != null) {
                        replaceAllUsesWith(inst, result);
                        toRemove.add(inst);
                        changed = true;
                    }
                }
                
                // 统一移除死指令
                bb.getInstructions().removeAll(toRemove);
            }
        }
    }
    
    /**
     * [Fix] 操作数归一化：对于可交换运算，将常数放到右边
     * 这样 5 * x 变成 x * 5，便于后续处理
     * 支持: ADD, MUL, AND, OR (XOR 移除)
     */
    private boolean canonicalizeOperands(Instruction inst) {
        if (!(inst instanceof BinaryOpInst bin)) {
            return false;
        }
        
        BinaryOpInst.OpCode op = bin.getOp();
        // 只处理可交换运算
        if (op != BinaryOpInst.OpCode.ADD && 
            op != BinaryOpInst.OpCode.MUL &&
            op != BinaryOpInst.OpCode.AND &&
            op != BinaryOpInst.OpCode.OR) {
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
        
        // 检查左右操作数特殊值
        boolean rhsIsZero = isConstant(rhs, 0);
        boolean lhsIsZero = isConstant(lhs, 0);
        boolean rhsIsOne = isConstant(rhs, 1);
        boolean lhsIsOne = isConstant(lhs, 1);
        boolean rhsIsMinusOne = isConstant(rhs, -1);
        boolean lhsIsMinusOne = isConstant(lhs, -1);
        
        // 自操作检查 (x op x)
        boolean isSame = (lhs == rhs);

        return switch (op) {
            case ADD -> {
                // x + 0 -> x
                if (rhsIsZero) yield lhs;
                if (lhsIsZero) yield rhs;
                yield null;
            }
            case SUB -> {
                // x - 0 -> x
                if (rhsIsZero) yield lhs;
                // x - x -> 0 (non-constant self-sub)
                if (isSame) yield new ConstantInt(0);
                yield null;
            }
            case MUL -> {
                // x * 0 -> 0
                if (rhsIsZero || lhsIsZero) yield new ConstantInt(0);
                // x * 1 -> x
                if (rhsIsOne) yield lhs;
                if (lhsIsOne) yield rhs;
                yield null;
            }
            case SDIV -> {
                // x / 1 -> x
                if (rhsIsOne) yield lhs;
                // x / x -> 1 (Assume x != 0 for basic simplicity, UB otherwise)
                if (isSame) yield new ConstantInt(1);
                // 0 / x -> 0
                if (lhsIsZero && !rhsIsZero) yield new ConstantInt(0);
                yield null;
            }
            case SREM -> {
                // x % 1 -> 0
                if (rhsIsOne) yield new ConstantInt(0);
                // x % x -> 0
                if (isSame) yield new ConstantInt(0);
                // 0 % x -> 0
                if (lhsIsZero && !rhsIsZero) yield new ConstantInt(0);
                yield null;
            }
            case AND -> {
                // x & 0 -> 0
                if (rhsIsZero || lhsIsZero) yield new ConstantInt(0);
                // x & -1 -> x
                if (rhsIsMinusOne) yield lhs;
                if (lhsIsMinusOne) yield rhs;
                // x & x -> x
                if (isSame) yield lhs;
                yield null;
            }
            case OR -> {
                // x | 0 -> x
                if (rhsIsZero) yield lhs;
                if (lhsIsZero) yield rhs;
                // x | -1 -> -1 (all ones)
                if (rhsIsMinusOne || lhsIsMinusOne) yield new ConstantInt(-1);
                // x | x -> x
                if (isSame) yield lhs;
                yield null;
            }
            default -> null; // XOR 均已移除
        };
    }
    
    private Value trySimplifyIcmp(IcmpInst icmp) {
        Value lhs = icmp.getLhs();
        Value rhs = icmp.getRhs();
        
        // 处理 x cmp x
        if (lhs == rhs) {
            return switch (icmp.getCmpType()) {
                // x == x -> True
                // x <= x -> True
                // x >= x -> True
                case EQ, SLE, SGE -> new ConstantInt(1);
                
                // x != x -> False
                // x < x  -> False
                // x > x  -> False
                case NE, SLT, SGT -> new ConstantInt(0);
            };
        }
        
        return null;
    }

    private boolean isConstant(Value v, int target) {
        return v instanceof ConstantInt c && c.getValue() == target;
    }

    private void replaceAllUsesWith(Value oldValue, Value newValue) {
        List<Use> usersSnapshot = new ArrayList<>(oldValue.getUsers());
        for (Use use : usersSnapshot) {
            use.setValue(newValue);
        }
    }
}
