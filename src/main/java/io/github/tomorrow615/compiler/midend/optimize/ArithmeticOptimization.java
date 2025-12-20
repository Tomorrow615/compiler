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
 * - x / 2^k -> 移位+修正    (除以 2 的幂次，正确处理正负数)
 */
public class ArithmeticOptimization implements Pass {

    @Override
    public String getName() {
        return "ArithmeticOptimization";
    }

    @Override
    public void runOnFunction(Function function) {
        for (BasicBlock bb : function.getBasicBlocks()) {
            // 使用新列表构建，因为我们可能需要插入多条指令
            List<Instruction> newInstructions = new ArrayList<>();
            
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof BinaryOpInst bin) {
                    List<Instruction> replacement = tryOptimize(bin);
                    if (replacement != null && !replacement.isEmpty()) {
                        // 有优化结果，添加所有替换指令
                        newInstructions.addAll(replacement);
                        // 最后一条指令是最终结果，替换所有使用
                        Instruction resultInst = replacement.get(replacement.size() - 1);
                        replaceAllUsesWith(bin, resultInst);
                        // 清理原指令的操作数关系
                        cleanupOperands(bin);
                        continue;
                    }
                }
                // 无优化，保留原指令
                newInstructions.add(inst);
            }
            
            // 替换整个指令列表
            bb.getInstructions().clear();
            bb.getInstructions().addAll(newInstructions);
        }
    }

    /**
     * 尝试优化二元运算
     * @return 替换指令列表（最后一条是最终结果），如果无法优化返回 null
     */
    private List<Instruction> tryOptimize(BinaryOpInst bin) {
        Value lhs = bin.getLhs();
        Value rhs = bin.getRhs();
        BinaryOpInst.OpCode op = bin.getOp();
        
        // 只处理右操作数是常量的情况
        if (!(rhs instanceof ConstantInt constRhs)) {
            return null;
        }
        
        int value = constRhs.getValue();
        
        // 处理乘法
        if (op == BinaryOpInst.OpCode.MUL) {
            return tryOptimizeMul(bin, lhs, value);
        }
        
        // 除法和取模只处理正的 2 的幂次
        if (value <= 0 || !isPowerOfTwo(value)) {
            return null;
        }
        
        int k = log2(value);
        
        return switch (op) {
            case SDIV -> {
                // x / 2^k 的正确实现（处理正负数）
                // 算法：result = (x + ((x >> 31) & (2^k - 1))) >> k
                // 
                // 对于正数 x：x >> 31 = 0，修正值 = 0，result = x >> k ✓
                // 对于负数 x：x >> 31 = -1，修正值 = 2^k - 1，向零舍入 ✓
                
                if (k == 0) {
                    // x / 1 = x，已在 AlgebraicSimplification 处理
                    yield null;
                }
                
                List<Instruction> result = new ArrayList<>();
                
                // t1 = x >> 31 (算术右移，获取符号扩展)
                Instruction t1 = new BinaryOpInst(
                    BinaryOpInst.OpCode.ASHR,
                    lhs,
                    new ConstantInt(31),
                    bin.getName() + "_sign",
                    null
                );
                result.add(t1);
                
                // t2 = t1 & (2^k - 1)  (负数得 2^k-1，正数得 0)
                Instruction t2 = new BinaryOpInst(
                    BinaryOpInst.OpCode.AND,
                    t1,
                    new ConstantInt((1 << k) - 1),
                    bin.getName() + "_mask",
                    null
                );
                result.add(t2);
                
                // t3 = x + t2  (加上修正值)
                Instruction t3 = new BinaryOpInst(
                    BinaryOpInst.OpCode.ADD,
                    lhs,
                    t2,
                    bin.getName() + "_adj",
                    null
                );
                result.add(t3);
                
                // final = t3 >> k  (最终右移)
                Instruction finalResult = new BinaryOpInst(
                    BinaryOpInst.OpCode.ASHR,
                    t3,
                    new ConstantInt(k),
                    bin.getName(),
                    null
                );
                result.add(finalResult);
                
                yield result;
            }
            case SREM -> {
                // x % 2^k 的正确实现
                // 算法：result = x - (x / 2^k) * 2^k
                // 即：result = x - ((x + ((x >> 31) & (2^k - 1))) >> k << k)
                
                if (k == 0) {
                    // x % 1 = 0，已在 AlgebraicSimplification 处理
                    yield null;
                }
                
                List<Instruction> result = new ArrayList<>();
                
                // 复用除法逻辑
                // t1 = x >> 31
                Instruction t1 = new BinaryOpInst(
                    BinaryOpInst.OpCode.ASHR,
                    lhs,
                    new ConstantInt(31),
                    bin.getName() + "_sign",
                    null
                );
                result.add(t1);
                
                // t2 = t1 & (2^k - 1)
                Instruction t2 = new BinaryOpInst(
                    BinaryOpInst.OpCode.AND,
                    t1,
                    new ConstantInt((1 << k) - 1),
                    bin.getName() + "_mask",
                    null
                );
                result.add(t2);
                
                // t3 = x + t2
                Instruction t3 = new BinaryOpInst(
                    BinaryOpInst.OpCode.ADD,
                    lhs,
                    t2,
                    bin.getName() + "_adj",
                    null
                );
                result.add(t3);
                
                // t4 = t3 >> k (除法结果)
                Instruction t4 = new BinaryOpInst(
                    BinaryOpInst.OpCode.ASHR,
                    t3,
                    new ConstantInt(k),
                    bin.getName() + "_div",
                    null
                );
                result.add(t4);
                
                // t5 = t4 << k (乘回来)
                Instruction t5 = new BinaryOpInst(
                    BinaryOpInst.OpCode.SHL,
                    t4,
                    new ConstantInt(k),
                    bin.getName() + "_mul",
                    null
                );
                result.add(t5);
                
                // final = x - t5 (取余数)
                Instruction finalResult = new BinaryOpInst(
                    BinaryOpInst.OpCode.SUB,
                    lhs,
                    t5,
                    bin.getName(),
                    null
                );
                result.add(finalResult);
                
                yield result;
            }
            default -> null;
        };
    }

    /**
     * 乘法优化
     * 支持：2的幂次、小常数展开
     */
    private List<Instruction> tryOptimizeMul(BinaryOpInst bin, Value lhs, int value) {
        // 负数暂不优化
        if (value <= 0) {
            return null;
        }
        
        // Case 1: 2的幂次 -> 直接左移
        if (isPowerOfTwo(value)) {
            int k = log2(value);
            Instruction shl = new BinaryOpInst(
                BinaryOpInst.OpCode.SHL,
                lhs,
                new ConstantInt(k),
                bin.getName(),
                null
            );
            return List.of(shl);
        }
        
        // Case 2: 2^k - 1 (如 3, 7, 15, 31...) -> (x << k) - x
        if (isPowerOfTwo(value + 1)) {
            int k = log2(value + 1);
            List<Instruction> result = new ArrayList<>();
            
            // t1 = x << k
            Instruction t1 = new BinaryOpInst(
                BinaryOpInst.OpCode.SHL,
                lhs,
                new ConstantInt(k),
                bin.getName() + "_shl",
                null
            );
            result.add(t1);
            
            // result = t1 - x
            Instruction finalResult = new BinaryOpInst(
                BinaryOpInst.OpCode.SUB,
                t1,
                lhs,
                bin.getName(),
                null
            );
            result.add(finalResult);
            
            return result;
        }
        
        // Case 3: 2^k + 1 (如 3, 5, 9, 17...) -> (x << k) + x
        if (isPowerOfTwo(value - 1)) {
            int k = log2(value - 1);
            List<Instruction> result = new ArrayList<>();
            
            // t1 = x << k
            Instruction t1 = new BinaryOpInst(
                BinaryOpInst.OpCode.SHL,
                lhs,
                new ConstantInt(k),
                bin.getName() + "_shl",
                null
            );
            result.add(t1);
            
            // result = t1 + x
            Instruction finalResult = new BinaryOpInst(
                BinaryOpInst.OpCode.ADD,
                t1,
                lhs,
                bin.getName(),
                null
            );
            result.add(finalResult);
            
            return result;
        }
        
        // Case 4: 2^a + 2^b (如 6=4+2, 10=8+2, 12=8+4...) -> (x << a) + (x << b)
        // 尝试分解为两个 2 的幂次之和
        for (int a = 1; a < 16; a++) {
            int remainder = value - (1 << a);
            if (remainder > 0 && isPowerOfTwo(remainder)) {
                int b = log2(remainder);
                List<Instruction> result = new ArrayList<>();
                
                // t1 = x << a
                Instruction t1 = new BinaryOpInst(
                    BinaryOpInst.OpCode.SHL,
                    lhs,
                    new ConstantInt(a),
                    bin.getName() + "_shl1",
                    null
                );
                result.add(t1);
                
                // t2 = x << b
                Instruction t2 = new BinaryOpInst(
                    BinaryOpInst.OpCode.SHL,
                    lhs,
                    new ConstantInt(b),
                    bin.getName() + "_shl2",
                    null
                );
                result.add(t2);
                
                // result = t1 + t2
                Instruction finalResult = new BinaryOpInst(
                    BinaryOpInst.OpCode.ADD,
                    t1,
                    t2,
                    bin.getName(),
                    null
                );
                result.add(finalResult);
                
                return result;
            }
        }
        
        // Case 5: 2^a - 2^b (如 14=16-2, 28=32-4...) -> (x << a) - (x << b)
        for (int a = 2; a < 16; a++) {
            int remainder = (1 << a) - value;
            if (remainder > 0 && isPowerOfTwo(remainder)) {
                int b = log2(remainder);
                List<Instruction> result = new ArrayList<>();
                
                // t1 = x << a
                Instruction t1 = new BinaryOpInst(
                    BinaryOpInst.OpCode.SHL,
                    lhs,
                    new ConstantInt(a),
                    bin.getName() + "_shl1",
                    null
                );
                result.add(t1);
                
                // t2 = x << b
                Instruction t2 = new BinaryOpInst(
                    BinaryOpInst.OpCode.SHL,
                    lhs,
                    new ConstantInt(b),
                    bin.getName() + "_shl2",
                    null
                );
                result.add(t2);
                
                // result = t1 - t2
                Instruction finalResult = new BinaryOpInst(
                    BinaryOpInst.OpCode.SUB,
                    t1,
                    t2,
                    bin.getName(),
                    null
                );
                result.add(finalResult);
                
                return result;
            }
        }
        
        // 无法优化
        return null;
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
