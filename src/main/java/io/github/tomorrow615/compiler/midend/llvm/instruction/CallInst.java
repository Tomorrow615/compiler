package io.github.tomorrow615.compiler.midend.llvm.instruction;

import io.github.tomorrow615.compiler.midend.llvm.type.FunctionType;
import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CallInst extends Instruction {

    /**
     * 构造 'call <ret_type> @<func_name>(<arg_types...>)
     * @param function 被调用的函数
     * @param args 传递的参数列表
     * @param name 接收返回值的名字 (如果 non-void)
     * @param parentBlock 插入到的基本块
     */
    public CallInst(Function function, List<Value> args, String name, BasicBlock parentBlock) {
        // call 指令的类型是函数的返回类型
        super(function.getReturnType(), name, parentBlock);

        // 操作数0: 被调用的函数
        this.addOperand(function);

        // 操作数 1...n: 函数参数
        for (Value arg : args) {
            this.addOperand(arg);
        }
    }

    public Function getFunction() {
        // 操作数0 始终是被调用的函数
        return (Function) this.getOperand(0);
    }

    public List<Value> getArguments() {
        // 操作数从 1 开始是参数
        return this.operands.subList(1, this.operands.size()).stream()
                .map(use -> use.getValue())
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Function func = getFunction();
        Type retType = func.getReturnType();

        if (retType.isVoidType()) {
            // e.g., call void @putint(i32 %1)
            sb.append("call void ");
        } else {
            // e.g., %2 = call i32 @getint()
            sb.append(this.getName()).append(" = call ").append(retType.toString()).append(" ");
        }

        // 打印函数名 (e.g., @getint)
        sb.append(func.getName());

        // 打印参数列表
        sb.append("(");
        String argsStr = getArguments().stream()
                .map(arg -> arg.getType().toString() + " " + arg.getName())
                .collect(Collectors.joining(", "));
        sb.append(argsStr).append(")");

        return sb.toString();
    }
}
