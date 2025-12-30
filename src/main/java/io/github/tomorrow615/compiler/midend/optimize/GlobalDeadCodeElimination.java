package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;

import java.util.*;

/**
 * 全局死代码消除 Pass (Global Dead Code Elimination)
 * 
 * 功能：
 * 移除从未被调用的函数 (Dead Functions)。
 * 从 main 函数开始进行可达性分析，未被标记为可达的函数将被一并删除。
 * 
 * 场景：
 * 激进内联 (Inlining) 后，很多小函数被并在调用者中，原函数体可能不再被引用。
 * 此时移除它们可以显著减小代码体积，减轻后端压力。
 */
public class GlobalDeadCodeElimination implements Pass {

    @Override
    public String getName() {
        return "GlobalDCE";
    }

    @Override
    public void runOnModule(Module module) {
        Set<Function> reachableFunctions = new HashSet<>();
        Queue<Function> workList = new LinkedList<>();

        // 1. 找到入口函数 (main)
        Function mainFunc = module.getFunctions().stream()
                .filter(f -> f.getName().equals("@main"))
                .findFirst()
                .orElse(null);

        if (mainFunc == null) return;

        // 2. 标记阶段 (Mark)
        reachableFunctions.add(mainFunc);
        workList.add(mainFunc);

        while (!workList.isEmpty()) {
            Function current = workList.poll();

            // 扫描函数体内的所有调用指令
            if (current.isDeclaration()) continue;
            
            for (BasicBlock bb : current.getBasicBlocks()) {
                for (Instruction inst : bb.getInstructions()) {
                    if (inst instanceof CallInst call) {
                        Function target = call.getFunction();
                        
                        // 由于递归或相互调用，可能已经访问过
                        if (!reachableFunctions.contains(target)) {
                            reachableFunctions.add(target);
                            workList.add(target);
                        }
                    }
                }
            }
        }

        // 3. 扫描与清除阶段 (Sweep)
        // 外部函数(Declaration)即使没被调用通常也不归我们管，但如果是库函数且没用...
        // 这里的策略是：保留所有声明（库函数），只移除定义的函数
        List<Function> toRemove = new ArrayList<>();
        
        for (Function func : module.getFunctions()) {
            if (func.isDeclaration()) continue; // 库函数声明保留
            
            if (!reachableFunctions.contains(func)) {
                // System.out.println("GlobalDCE: Removing dead function " + func.getName());
                toRemove.add(func);
            }
        }

        module.getFunctions().removeAll(toRemove);
    }

    @Override
    public void runOnFunction(Function function) {
        // GlobalDCE is a module-level pass, nothing to do per function.
    }
}
