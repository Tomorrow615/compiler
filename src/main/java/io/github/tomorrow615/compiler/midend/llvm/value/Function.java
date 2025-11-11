package io.github.tomorrow615.compiler.midend.llvm.value;

import io.github.tomorrow615.compiler.midend.llvm.type.FunctionType;
import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Function extends GlobalValue {

    private final List<BasicBlock> basicBlocks;
    private final List<Argument> arguments;
    private final FunctionType functionType;

    public Function(FunctionType type, String name) {
        // 函数本身是一个指向其函数签名的全局指针
        super(new PointerType(type), name);
        this.functionType = type;
        this.basicBlocks = new ArrayList<>();
        this.arguments = new ArrayList<>();

        // 根据 FunctionType 创建 Argument 对象
        int i = 0;
        for (Type paramType : type.getParamTypes()) {
            // 参数的命名将在 SlotTracker 中完成
            this.arguments.add(new Argument(paramType, "%" + i, this, i++));
        }
    }

    public List<BasicBlock> getBasicBlocks() {
        return basicBlocks;
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    public FunctionType getFunctionType() {
        return functionType;
    }

    public Type getReturnType() {
        return this.functionType.getReturnType();
    }

    public void addBasicBlock(BasicBlock bb) {
        this.basicBlocks.add(bb);
    }

    // 用于 declare 声明
    public boolean isDeclaration() {
        return this.basicBlocks.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // 打印函数声明或定义
        // e.g., declare i32 @getint()
        if (isDeclaration()) {
            sb.append("declare ");
        } else {
            // e.g., define dso_local i32 @main(...)
            sb.append("define dso_local ");
        }

        // 打印返回值类型
        sb.append(this.getReturnType().toString()).append(" ");

        // 打印函数名
        sb.append(this.getName());

        // 打印参数
        sb.append("(");
        String params = this.arguments.stream()
                .map(arg -> arg.getType().toString() + " " + arg.getName())
                .collect(Collectors.joining(", "));
        sb.append(params).append(")");

        // 如果只是声明，到此结束
        if (isDeclaration()) {
            return sb.toString();
        }

        // 打印函数体
        sb.append(" {\n");
        for (BasicBlock bb : basicBlocks) {
            sb.append(bb.toString()); // BasicBlock.toString() 会处理指令
        }
        sb.append("}\n");

        return sb.toString();
    }
}
