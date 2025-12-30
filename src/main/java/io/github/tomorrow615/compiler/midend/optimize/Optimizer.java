package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Argument;
import io.github.tomorrow615.compiler.midend.llvm.instruction.Instruction;

public class Optimizer {
    // 最大的内联迭代次数
    private static final int MAX_INLINE_ITERATIONS = 15;
    // 内部清理循环的次数
    private static final int MAX_INNER_ITERATIONS = 3;
    // 熔断阈值
    private static final int MAX_INSTRUCTION_THRESHOLD = 1000;

    public void run(Module module) {
        // === Phase 1: 预处理 ===
        runPass(module, new SROA_Simple());
        runPass(module, new Global2Local());
        runPass(module, new Mem2Reg());
        runPass(module, new SimplifyCFG());

        // === Phase 2: 核心迭代 ===
        for (int i = 0; i < MAX_INLINE_ITERATIONS; i++) {
            int before = countModuleInstructions(module);

            // 1. 内联一层
            runPass(module, new FunctionInlining());

            // 2. 清理内联产生的 alloca 和冗余计算
            runPass(module, new SROA_Simple());
            runPass(module, new Mem2Reg());

            // 灵活调整
            //if (i % 2 == 0) {
            runPass(module, new GVN());
            //}

            // 3. 粉碎死代码
            for (int j = 0; j < MAX_INNER_ITERATIONS; j++) {
                int innerBefore = countModuleInstructions(module);

                runPass(module, new ConstantFolding());     // 算出分支条件
                runPass(module, new SimplifyCFG());         // 剪除死路径
                runPass(module, new DeadCodeElimination()); // 移除无用指令
                runPass(module, new ArithmeticOptimization());

                int innerAfter = countModuleInstructions(module);
                if (innerBefore == innerAfter) break;
            }

            int after = countModuleInstructions(module);

            if (before == after) break;

            if (after > MAX_INSTRUCTION_THRESHOLD) {
                break;
            }
        }

        // 内联结束后，移除不再使用的死函数
        runPass(module, new GlobalDeadCodeElimination());

        // === Phase 3: 收尾优化 ===
        runPass(module, new AlgebraicSimplification());
        runPass(module, new ArithmeticOptimization());
        runPass(module, new LICM());              // 循环不变式外提
        runPass(module, new StrengthReduction()); // 强度削减 (乘法变移位)
        runPass(module, new GVN());               // 最终去重
        runPass(module, new SimplifyCFG());
        runPass(module, new DeadCodeElimination());

        renumberValues(module);
    }

    private void runPass(Module module, Pass pass) {
        pass.runOnModule(module);
    }

    private int countModuleInstructions(Module module) {
        int count = 0;
        for (Function func : module.getFunctions()) {
            if (func.isDeclaration()) continue;
            for (BasicBlock bb : func.getBasicBlocks()) {
                count += bb.getInstructions().size();
            }
        }
        return count;
    }

    private void renumberValues(Module module) {
        for (Function func : module.getFunctions()) {
            if (func.isDeclaration()) continue;

            int counter = 0;

            // 1. 参数编号
            for (Argument arg : func.getArguments()) {
                arg.setName(String.valueOf(counter++));
            }

            // 2. 基本块与指令编号
            for (BasicBlock bb : func.getBasicBlocks()) {
                String bbName = bb.getName();
                if (bbName == null || bbName.isEmpty() || bbName.startsWith("<??")) {
                    bb.setName("label_" + counter++);
                }

                for (Instruction inst : bb.getInstructions()) {
                    if (!inst.getType().isVoidType()) {
                        String instName = inst.getName();
                        if (instName == null || instName.isEmpty() || instName.startsWith("<??")) {
                            inst.setName(String.valueOf(counter++));
                        }
                    }
                }
            }
        }
    }
}
