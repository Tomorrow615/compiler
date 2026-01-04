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
        // 目标：在进行昂贵的分析之前，先简化 IR
        if (Config.OPT_SROA) runPass(module, new SROA_Simple());
        if (Config.OPT_GLOBAL2LOCAL) runPass(module, new Global2Local());
        if (Config.OPT_PROMOTE_STATIC_LOCAL) runPass(module, new PromoteStaticLocal());
        if (Config.OPT_SIMPLIFY_CFG) runPass(module, new SimplifyCFG());
        if (Config.OPT_MEM2REG) runPass(module, new Mem2Reg());
        if (Config.OPT_MEM_FORWARD) runPass(module, new MemForward());
        if (Config.OPT_DCE) runPass(module, new DeadCodeElimination());

        // === Phase 2: 核心迭代 ===
        for (int i = 0; i < Config.MAX_INLINE_ITERATIONS; i++) {
            int before = countModuleInstructions(module);

            // 1. 内联一层
            if (Config.OPT_INLINING) runPass(module, new FunctionInlining());

            // 2. 内联后立即清理 CFG（合并基本块，移除不可达分支）
            if (Config.OPT_SIMPLIFY_CFG) runPass(module, new SimplifyCFG());
            if (Config.OPT_DCE) runPass(module, new DeadCodeElimination());

            // 3. 清理内联产生的 alloca 和冗余计算
            if (Config.OPT_SROA) runPass(module, new SROA_Simple());
            if (Config.OPT_MEM2REG) runPass(module, new Mem2Reg());
            if (Config.OPT_GVN) runPass(module, new GVN());

            // 4. 内部清理循环
            for (int j = 0; j < Config.MAX_INNER_ITERATIONS; j++) {
                int innerBefore = countModuleInstructions(module);

                // A. 基础计算清理
                if (Config.OPT_CONST_FOLDING) runPass(module, new ConstantFolding());
                if (Config.OPT_ALGEBRAIC) runPass(module, new AlgebraicSimplification());
                if (Config.OPT_CONST_FOLDING) runPass(module, new ConstantFolding()); // 再次折叠 AlgebraicSimplification 产生的常量
                if (Config.OPT_GVN) runPass(module, new GVN());

                // B. 内存提升（为 LICM 铺路）
                if (Config.OPT_SROA) runPass(module, new SROA_Simple());
                if (Config.OPT_MEM2REG) runPass(module, new Mem2Reg());
                if (Config.OPT_MEM_FORWARD) runPass(module, new MemForward());

                // C. 循环变换
                if (Config.OPT_STRENGTH_REDUCE) runPass(module, new StrengthReduction());
                if (Config.OPT_LICM) runPass(module, new LICM()); // 此时 LICM 能看到更多被提升的寄存器
                if (Config.OPT_LOOP_UNROLL) runPass(module, new LoopUnrolling());

                // D. 展开后的充分清理
                if (Config.OPT_SROA) runPass(module, new SROA_Simple()); // 处理展开产生的 alloca
                if (Config.OPT_MEM2REG) runPass(module, new Mem2Reg());
                if (Config.OPT_GVN) runPass(module, new GVN());
                if (Config.OPT_LICM) runPass(module, new LICM()); // 处理展开暴露的新循环不变量
                if (Config.OPT_DCE) runPass(module, new DeadCodeElimination());
                if (Config.OPT_SIMPLIFY_CFG) runPass(module, new SimplifyCFG());
                if (Config.OPT_ARITHMETIC) runPass(module, new ArithmeticOptimization()); // 展开可能暴露更多除法优化机会

                int innerAfter = countModuleInstructions(module);
                if (innerBefore == innerAfter) break;
            }

            int after = countModuleInstructions(module);
            if (before == after) break;
            if (after > Config.MAX_INSTRUCTION_THRESHOLD) break;
        }

        // 内联结束后，移除不再使用的死函数
        if (Config.OPT_GLOBAL_DCE) runPass(module, new GlobalDeadCodeElimination());

        // === Phase 3: 收尾优化（迭代执行）===
        for (int k = 0; k < 3; k++) {
            int phase3Before = countModuleInstructions(module);


            if (Config.OPT_ALGEBRAIC) runPass(module, new AlgebraicSimplification());
            if (Config.OPT_CONST_FOLDING) runPass(module, new ConstantFolding());
            if (Config.OPT_ARITHMETIC) runPass(module, new ArithmeticOptimization());
            // 内存提升：让循环优化在寄存器形式下工作
            if (Config.OPT_SROA) runPass(module, new SROA_Simple());
            if (Config.OPT_MEM2REG) runPass(module, new Mem2Reg());
            if (Config.OPT_MEM_FORWARD) runPass(module, new MemForward());
            if (Config.OPT_LICM) runPass(module, new LICM());
            if (Config.OPT_STRENGTH_REDUCE) runPass(module, new StrengthReduction());
            if (Config.OPT_GVN) runPass(module, new GVN());
            // DCE 在 SimplifyCFG 之前：删除指令后可能让基本块变空，便于合并
            if (Config.OPT_DCE) runPass(module, new DeadCodeElimination());
            if (Config.OPT_SIMPLIFY_CFG) runPass(module, new SimplifyCFG());

            int phase3After = countModuleInstructions(module);
            if (phase3Before == phase3After) break;
        }

        // 最后一次 SimplifyCFG：确保所有空块都被合并，减少 JUMP 指令
        if (Config.OPT_SIMPLIFY_CFG) runPass(module, new SimplifyCFG());

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
