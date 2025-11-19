package io.github.tomorrow615.compiler.midend.llvm;

import io.github.tomorrow615.compiler.midend.llvm.value.Function;
import io.github.tomorrow615.compiler.midend.llvm.value.GlobalVariable;
import io.github.tomorrow615.compiler.util.*;

import java.util.ArrayList;
import java.util.List;

public class Module {
    private final List<GlobalVariable> globalVariables;
    private final List<Function> functions;

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

    public String toString(SlotTracker tracker) {
        StringBuilder sb = new StringBuilder();
        if (!globalVariables.isEmpty()) {
            for (GlobalVariable gv : globalVariables) {
                sb.append(gv.toString(tracker)).append("\n");
            }
            sb.append("\n");
        }
        for (Function func : functions) {
            sb.append(func.toString(tracker)).append("\n");
        }
        return sb.toString();
    }
}
