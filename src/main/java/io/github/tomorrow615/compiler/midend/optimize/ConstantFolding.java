package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 常量折叠优化 Pass
 * 在编译期计算常量表达式的结果
 * 
 * 例如: %1 = add i32 2, 3  ->  替换所有 %1 的使用为 5
 */
public class ConstantFolding implements Pass {

    @Override
    public String getName() {
        return "ConstantFolding";
    }

    @Override
    public void runOnFunction(Function function) {
        boolean changed = true;
        
        // 迭代直到没有更多可折叠的常量
        while (changed) {
            changed = false;
            
            for (BasicBlock bb : function.getBasicBlocks()) {
                List<Instruction> toRemove = new ArrayList<>();
                
                for (Instruction inst : bb.getInstructions()) {
                    ConstantInt result = tryFold(inst);
                    if (result != null) {
                        // 将所有使用该指令的地方替换为常量
                        replaceAllUsesWith(inst, result);
                        toRemove.add(inst);
                        changed = true;
                    }
                }
                
                // 删除已折叠的指令
                bb.getInstructions().removeAll(toRemove);
            }
        }
    }

    /**
     * 尝试折叠指令，返回折叠后的常量，如不能折叠返回 null
     */
    private ConstantInt tryFold(Instruction inst) {
        if (inst instanceof BinaryOpInst bin) {
            return foldBinaryOp(bin);
        }
        // 可扩展：IcmpInst 等
        return null;
    }

    /**
     * 折叠二元运算
     */
    private ConstantInt foldBinaryOp(BinaryOpInst bin) {
        Value lhs = bin.getLhs();
        Value rhs = bin.getRhs();
        
        // 只有两边都是常量才能折叠
        if (!(lhs instanceof ConstantInt c1) || !(rhs instanceof ConstantInt c2)) {
            return null;
        }
        
        int v1 = c1.getValue();
        int v2 = c2.getValue();
        int result;
        
        result = switch (bin.getOp()) {
            case ADD -> v1 + v2;
            case SUB -> v1 - v2;
            case MUL -> v1 * v2;
            case SDIV -> {
                if (v2 == 0) yield 0; // 避免除零，保守处理
                yield v1 / v2;
            }
            case SREM -> {
                if (v2 == 0) yield 0; // 避免除零，保守处理
                yield v1 % v2;
            }
            case AND -> v1 & v2;
            case OR -> v1 | v2;
            case SHL -> v1 << v2;
            case ASHR -> v1 >> v2;
        };
        
        return new ConstantInt(result);
    }

    /**
     * 将所有使用 oldValue 的地方替换为 newValue
     */
    private void replaceAllUsesWith(Value oldValue, Value newValue) {
        // 复制一份 users 列表，因为替换时会修改原列表
        List<Use> usersSnapshot = new ArrayList<>(oldValue.getUsers());
        
        for (Use use : usersSnapshot) {
            use.setValue(newValue);
        }
    }
}
