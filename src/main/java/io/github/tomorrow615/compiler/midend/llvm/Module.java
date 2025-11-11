package io.github.tomorrow615.compiler.midend.llvm;

import io.github.tomorrow615.compiler.midend.llvm.value.Function;
import io.github.tomorrow615.compiler.midend.llvm.value.GlobalVariable;

import java.util.ArrayList;
import java.util.List;

public class Module {

    private final List<GlobalVariable> globalVariables;
    private final List<Function> functions;
    // 还可以添加 target triple, datalayout 等元信息

    public Module() {
        this.globalVariables = new ArrayList<>();
        this.functions = new ArrayList<>();
    }

    public List<GlobalVariable> getGlobalVariables() {
        return globalVariables;
    }

    public List<Function> getFunctions() {
        return functions;
    }

    public void addGlobalVariable(GlobalVariable gv) {
        this.globalVariables.add(gv);
    }

    public void addFunction(Function func) {
        this.functions.add(func);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // 打印全局变量
        for (GlobalVariable gv : globalVariables) {
            sb.append(gv.toString()).append("\n");
        }
        sb.append("\n");

        // 打印函数
        for (Function func : functions) {
            sb.append(func.toString()).append("\n");
        }

        return sb.toString();
    }
}
