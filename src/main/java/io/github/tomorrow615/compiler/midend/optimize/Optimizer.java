package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Argument;
import io.github.tomorrow615.compiler.midend.llvm.instruction.Instruction;
import io.github.tomorrow615.compiler.util.Config;

public class Optimizer {

    public void run(Module module) {
        // === Phase 1: 预处理 ===
        if (Config.OPT_SROA) runPass(module, new SROA_Simple());
        if (Config.OPT_GLOBAL2LOCAL) runPass(module, new Global2Local());
        if (Config.OPT_MEM2REG) runPass(module, new Mem2Reg());
        if (Config.OPT_SIMPLIFY_CFG) runPass(module, new SimplifyCFG());

        // === Phase 2: 核心迭代 ===
        for (int i = 0; i < Config.MAX_INLINE_ITERATIONS; i++) {
            int before = countModuleInstructions(module);

            // 1. 内联一层
            if (Config.OPT_INLINING) runPass(module, new FunctionInlining());

            // 2. 清理内联产生的 alloca 和冗余计算
            if (Config.OPT_SROA) runPass(module, new SROA_Simple());
            if (Config.OPT_MEM2REG) runPass(module, new Mem2Reg());
            if (Config.OPT_GVN) runPass(module, new GVN());

            // 3. 粉碎死代码
            for (int j = 0; j < Config.MAX_INNER_ITERATIONS; j++) {
                int innerBefore = countModuleInstructions(module);

                if (Config.OPT_CONST_FOLDING) runPass(module, new ConstantFolding());
                if (Config.OPT_SIMPLIFY_CFG) runPass(module, new SimplifyCFG());
                if (Config.OPT_DCE) runPass(module, new DeadCodeElimination());
                if (Config.OPT_ARITHMETIC) runPass(module, new ArithmeticOptimization());
                
                // 循环展开（由 AGGRESSIVE_MODE 内部控制）
                runPass(module, new LoopUnrolling());
                
                // [Phase 4] 循环展开后立即进行标量替换和 SSA 提升
                // 目的：将展开后的数组访问 (alloca [N x i32]) 彻底转化为寄存器操作
                if (Config.OPT_SROA) runPass(module, new SROA_Simple());
                if (Config.OPT_MEM2REG) runPass(module, new Mem2Reg());
                if (Config.OPT_GVN) runPass(module, new GVN());
                
                // Unroll 之后必须清理 CFG 和死代码
                if (Config.OPT_SIMPLIFY_CFG) runPass(module, new SimplifyCFG());
                if (Config.OPT_DCE) runPass(module, new DeadCodeElimination());

                int innerAfter = countModuleInstructions(module);
                if (innerBefore == innerAfter) break;
            }

            int after = countModuleInstructions(module);
            if (before == after) break;
            if (after > Config.MAX_INSTRUCTION_THRESHOLD) break;
        }

        // 内联结束后，移除不再使用的死函数
        if (Config.OPT_GLOBAL_DCE) runPass(module, new GlobalDeadCodeElimination());

        // === Phase 3: 收尾优化 ===
        if (Config.OPT_ALGEBRAIC) runPass(module, new AlgebraicSimplification());
        if (Config.OPT_ARITHMETIC) runPass(module, new ArithmeticOptimization());
        if (Config.OPT_LICM) runPass(module, new LICM());
        if (Config.OPT_STRENGTH_REDUCE) runPass(module, new StrengthReduction());
        if (Config.OPT_GVN) runPass(module, new GVN());
        if (Config.OPT_SIMPLIFY_CFG) runPass(module, new SimplifyCFG());
        if (Config.OPT_DCE) runPass(module, new DeadCodeElimination());

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
                if (bbName == null || bbName.isEmpty() || bbName.startsWith("<?")) {
                    bb.setName("label_" + counter++);
                }

                for (Instruction inst : bb.getInstructions()) {
                    if (!inst.getType().isVoidType()) {
                        String instName = inst.getName();
                        if (instName == null || instName.isEmpty() || instName.startsWith("<?")) {
                            inst.setName(String.valueOf(counter++));
                        }
                    }
                }
            }
        }
    }
}
