package io.github.tomorrow615.compiler.util;

import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.value.*;
import io.github.tomorrow615.compiler.midend.llvm.instruction.Instruction;

import java.util.HashMap;
import java.util.Map;

public class SlotTracker {
    private final Map<Value, String> nameMap;
    private final Map<Function, Integer> funcValueCounters;

    public SlotTracker(Module module) {
        this.nameMap = new HashMap<>();
        this.funcValueCounters = new HashMap<>();
        traceModule(module);
    }

    private void traceModule(Module module) {
        for (GlobalVariable gv : module.getGlobalVariables()) {
            nameMap.put(gv, gv.getName());
        }
        for (Function func : module.getFunctions()) {
            nameMap.put(func, func.getName());
            traceFunction(func);
        }
    }

    private void traceFunction(Function func) {
        int counter = 0;
        for (Argument arg : func.getArguments()) {
            String name = "%" + counter++;
            arg.setName(name);
            nameMap.put(arg, name);
        }
        if (func.isDeclaration()) {
            return;
        }
        for (BasicBlock bb : func.getBasicBlocks()) {
            nameMap.put(bb, bb.getName());
            for (Instruction inst : bb.getInstructions()) {
                if (!inst.getType().isVoidType()) {
                    String name = "%" + counter++;
                    inst.setName(name);
                    nameMap.put(inst, name);
                }
            }
        }
        funcValueCounters.put(func, counter);
    }
    
    public String getName(Value val) {
        if (val instanceof ConstantInt) {
            return val.toString();
        }
        if (!nameMap.containsKey(val)) {
            return "<??" + val.hashCode() + "??>";
        }
        return nameMap.get(val);
    }
}
