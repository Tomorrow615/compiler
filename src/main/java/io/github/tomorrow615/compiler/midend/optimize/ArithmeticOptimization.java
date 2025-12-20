package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 乘除法优化 Pass
 * 
 * 将乘除法转换为更高效的移位运算：
 * - x * 2^k -> x << k      (乘以 2 的幂次转左移)
 * - x / 2^k -> x >> k      (除以 2 的幂次转右移，仅正数安全)
 * - x % 2^k -> x & (2^k-1) (取模 2 的幂次转按位与，仅正数安全)
 * 
 * 注意：有符号除法和取模对负数有特殊处理要求
 * 为保证正确性，这里只优化能确定符号的情况
 */
public class ArithmeticOptimization implements Pass {

    @Override
    public String getName() {
        return "ArithmeticOptimization";
    }

    @Override
    public void runOnFunction(Function function) {
        for (BasicBlock bb : function.getBasicBlocks()) {
            List<Instruction> instructions = bb.getInstructions();
            
            for (int i = 0; i < instructions.size(); i++) {
                Instruction inst = instructions.get(i);
                
                if (inst instanceof BinaryOpInst bin) {
                    Instruction replacement = tryOptimize(bin, bb);
                    if (replacement != null) {
                        // 替换所有使用
                        replaceAllUsesWith(bin, replacement);
                        // 替换列表中的位置
                        instructions.set(i, replacement);
                        // 从原指令断开操作数的 Use 关系
                        cleanupOperands(bin);
                    }
                }
            }
        }
    }

    private Instruction tryOptimize(BinaryOpInst bin, BasicBlock bb) {
        Value lhs = bin.getLhs();
        Value rhs = bin.getRhs();
        BinaryOpInst.OpCode op = bin.getOp();
        
        // 只处理右操作数是常量的情况
        if (!(rhs instanceof ConstantInt constRhs)) {
            return null;
        }
        
        int value = constRhs.getValue();
        
        // 必须是正的 2 的幂次
        if (value <= 0 || !isPowerOfTwo(value)) {
            return null;
        }
        
        int k = log2(value);
        
        return switch (op) {
            case MUL -> {
                // x * 2^k -> x << k
                Instruction shl = new BinaryOpInst(
                    BinaryOpInst.OpCode.SHL,
                    lhs,
                    new ConstantInt(k),
                    bin.getName(),
                    null  // 不自动添加到 BasicBlock
                );
                yield shl;
            }
            case SDIV -> {
                // x / 2^k -> x >> k (仅当 x >= 0 时正确)
                // 保守策略：只优化除以 1 的情况（已在 AlgebraicSimplification 处理）
                // 这里暂不优化有符号除法，因为负数需要额外处理
                yield null;
            }
            case SREM -> {
                // x % 2^k -> x & (2^k - 1) (仅当 x >= 0 时正确)
                // 保守策略：暂不优化
                yield null;
            }
            default -> null;
        };
    }

    private boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    private int log2(int n) {
        int k = 0;
        while ((1 << k) < n) {
            k++;
        }
        return k;
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
}
