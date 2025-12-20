package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.*;

/**
 * 公共子表达式消除 (Common Subexpression Elimination) Pass
 * 
 * 在同一基本块内，如果两个表达式的操作符和操作数完全相同，
 * 则第二个表达式可以被第一个的结果替代。
 * 
 * 例如:
 *   %1 = add i32 %a, %b
 *   %2 = add i32 %a, %b   <- 可以用 %1 替代
 * 
 * 注意：这是局部 CSE，只在单个基本块内进行，保证正确性。
 */
public class CommonSubexprElimination implements Pass {

    @Override
    public String getName() {
        return "CommonSubexprElimination";
    }

    @Override
    public void runOnFunction(Function function) {
        for (BasicBlock bb : function.getBasicBlocks()) {
            optimizeBlock(bb);
        }
    }

    /**
     * 在单个基本块内进行 CSE
     */
    private void optimizeBlock(BasicBlock bb) {
        // 表达式 -> 第一次计算该表达式的指令
        Map<ExpressionKey, Instruction> exprMap = new HashMap<>();
        // 需要删除的指令
        List<Instruction> toRemove = new ArrayList<>();
        
        for (Instruction inst : bb.getInstructions()) {
            // 只处理纯计算指令（无副作用）
            if (!isPureComputation(inst)) {
                continue;
            }
            
            ExpressionKey key = makeKey(inst);
            if (key == null) {
                continue;
            }
            
            Instruction existing = exprMap.get(key);
            if (existing != null) {
                // 找到公共子表达式，用已有结果替代
                replaceAllUsesWith(inst, existing);
                toRemove.add(inst);
            } else {
                // 第一次见到这个表达式，记录下来
                exprMap.put(key, inst);
            }
        }
        
        // 删除冗余指令
        for (Instruction inst : toRemove) {
            cleanupOperands(inst);
            bb.getInstructions().remove(inst);
        }
    }

    /**
     * 判断指令是否是纯计算（无副作用，可安全消除）
     */
    private boolean isPureComputation(Instruction inst) {
        // 二元运算：add, sub, mul, div, rem, and, or, shl, ashr
        if (inst instanceof BinaryOpInst) {
            return true;
        }
        // 比较运算
        if (inst instanceof IcmpInst) {
            return true;
        }
        // GEP（地址计算）
        if (inst instanceof GetElementPtrInst) {
            return true;
        }
        // Zext/Trunc（类型转换）
        if (inst instanceof ZextInst || inst instanceof TruncInst) {
            return true;
        }
        // 以下不能做 CSE：
        // - Load: 内存可能被修改
        // - Call: 可能有副作用
        // - Store, Branch, Return: 有副作用
        // - Phi: 依赖控制流
        // - Alloca: 每次分配不同地址
        return false;
    }

    /**
     * 为指令创建唯一的表达式键
     * 相同的操作符+操作数 -> 相同的键
     */
    private ExpressionKey makeKey(Instruction inst) {
        if (inst instanceof BinaryOpInst bin) {
            return new ExpressionKey(
                "binary_" + bin.getOp().name(),
                bin.getLhs(),
                bin.getRhs()
            );
        }
        if (inst instanceof IcmpInst icmp) {
            return new ExpressionKey(
                "icmp_" + icmp.getCmpType().name(),
                icmp.getLhs(),
                icmp.getRhs()
            );
        }
        if (inst instanceof GetElementPtrInst gep) {
            // GEP: 操作符 + 基地址 + 所有索引
            List<Value> operands = new ArrayList<>();
            operands.add(gep.getBasePtr());
            operands.addAll(gep.getIndices());
            return new ExpressionKey("gep", operands);
        }
        if (inst instanceof ZextInst zext) {
            return new ExpressionKey("zext", zext.getOperand(0));
        }
        if (inst instanceof TruncInst trunc) {
            return new ExpressionKey("trunc", trunc.getOperand(0));
        }
        return null;
    }

    private void replaceAllUsesWith(Value oldValue, Value newValue) {
        List<Use> usersSnapshot = new ArrayList<>(oldValue.getUsers());
        for (Use use : usersSnapshot) {
            use.setValue(newValue);
        }
    }

    private void cleanupOperands(Instruction inst) {
        for (Use use : inst.getOperands()) {
            Value operand = use.getValue();
            if (operand != null) {
                operand.removeUse(use);
            }
        }
    }

    /**
     * 表达式键：用于哈希表查找相同表达式
     */
    private static class ExpressionKey {
        private final String opcode;
        private final List<Value> operands;

        public ExpressionKey(String opcode, Value... operands) {
            this.opcode = opcode;
            this.operands = Arrays.asList(operands);
        }

        public ExpressionKey(String opcode, List<Value> operands) {
            this.opcode = opcode;
            this.operands = new ArrayList<>(operands);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ExpressionKey other)) return false;
            
            if (!opcode.equals(other.opcode)) return false;
            if (operands.size() != other.operands.size()) return false;
            
            // 比较每个操作数是否相同（使用对象引用相等）
            for (int i = 0; i < operands.size(); i++) {
                if (operands.get(i) != other.operands.get(i)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int result = opcode.hashCode();
            for (Value v : operands) {
                // 使用 System.identityHashCode 因为我们比较的是引用
                result = 31 * result + System.identityHashCode(v);
            }
            return result;
        }
    }
}
