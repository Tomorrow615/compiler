package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.util.*;
import io.github.tomorrow615.compiler.midend.llvm.type.*;

import java.util.List;
import java.util.stream.Collectors;

public class GetElementPtrInst extends Instruction {

    private final Type baseType; // GEP 的第一个 <ty> 参数，即指针指向的元素的类型

    public GetElementPtrInst(Value basePtr, List<Value> indices, String name, BasicBlock parentBlock) {

        // --- [ 修复: 'super()' 必须是第一行 ] ---
        // 1. 我们首先调用一个 static 辅助方法来计算返回类型
        super(calculateReturnType(basePtr, indices), name, parentBlock);

        // 2. 在 super() 调用*之后*，我们现在可以安全地设置 'this' 字段
        this.baseType = ((PointerType) basePtr.getType()).getTargetType();
        // --- [ 修复结束 ] ---

        // 3. 添加操作数 (不变)
        this.addOperand(basePtr);
        for (Value index : indices) {
            this.addOperand(index);
        }
    }

    private static Type calculateReturnType(Value basePtr, List<Value> indices) {
        // 1. 获取基指针指向的类型 (e.g., [9 x i8])
        Type baseType = ((PointerType) basePtr.getType()).getTargetType();

        // 2. 计算 GEP 的返回类型
        if (baseType instanceof ArrayType && indices.size() > 1) {
            // 如果是对数组用 GEP (e.g., gep [9 x i8], ..., i32 0, i32 0)
            // 结果是指向数组*元素*的指针 (e.g., i8*)
            return new PointerType(((ArrayType) baseType).getElementType());
        } else {
            // 默认回退 (e.g., GEP i32, i32* %p, i32 1)
            return new PointerType(baseType);
        }
    }

    public Value getBasePtr() {
        return this.getOperand(0);
    }

    public List<Value> getIndices() {
        return this.operands.subList(1, this.operands.size()).stream()
                .map(use -> use.getValue())
                .collect(Collectors.toList());
    }

    @Override
    public String toString(SlotTracker tracker) {
        // e.g., %1 = getelementptr [10 x i32], [10 x i32]* %a, i32 0, i32 %i
        // [cite: 1480] (引用原 toString 逻辑)

        StringBuilder sb = new StringBuilder();
        sb.append(tracker.getName(this)).append(" = getelementptr ");

        // 打印 baseType
        sb.append(this.baseType.toString()).append(", ");
        // 打印基指针 (操作数 0)
        sb.append(getBasePtr().getType().toString()).append(" ").append(tracker.getName(getBasePtr()));
        // 打印索引
        for (Value index : getIndices()) {
            sb.append(", ").append(index.getType().toString()).append(" ").append(tracker.getName(index));
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "GetElementPtrInst<" + this.name + ">@" + hashCode();
    }
}
