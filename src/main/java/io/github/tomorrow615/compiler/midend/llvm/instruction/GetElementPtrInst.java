package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;

import java.util.List;
import java.util.stream.Collectors;

public class GetElementPtrInst extends Instruction {

    private final Type baseType; // GEP 的第一个 <ty> 参数，即指针指向的元素的类型

    /**
     * 构造 'getelementptr' 指令
     * @param basePtr 基指针 (e.g., [10 x i32]* %a)
     * @param indices 索引列表 (e.g., i32 0, i32 %i)
     * @param name 结果指针的名字 (e.g., %gep.tmp)
     * @param parentBlock 插入到的基本块
     */
    public GetElementPtrInst(Value basePtr, List<Value> indices, String name, BasicBlock parentBlock) {
        // GEP 的结果总是一个指针
        // (这是一个简化，精确的返回类型推导比较复杂，但对于一维数组，
        // 返回指向元素类型的指针是正确的)
        super(new PointerType(((PointerType) basePtr.getType()).getTargetType()), name, parentBlock);

        // 第一个 <ty> 参数
        this.baseType = ((PointerType) basePtr.getType()).getTargetType();

        // 操作数0: 基指针
        this.addOperand(basePtr);

        // 操作数 1...n: 索引
        for (Value index : indices) {
            this.addOperand(index);
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
    public String toString() {
        // e.g., %1 = getelementptr [10 x i32], [10 x i32]* %a, i32 0, i32 %i
        //

        StringBuilder sb = new StringBuilder();
        sb.append(this.getName()).append(" = getelementptr ");

        // 打印 baseType
        sb.append(this.baseType.toString()).append(", ");

        // 打印基指针 (操作数 0)
        sb.append(getBasePtr().getType().toString()).append(" ").append(getBasePtr().getName());

        // 打印索引
        for (Value index : getIndices()) {
            sb.append(", ").append(index.getType().toString()).append(" ").append(index.getName());
        }

        return sb.toString();
    }
}
