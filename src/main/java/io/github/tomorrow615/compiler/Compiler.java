package io.github.tomorrow615.compiler;

import io.github.tomorrow615.compiler.frontend.error.*;
import io.github.tomorrow615.compiler.frontend.lexer.*;
import io.github.tomorrow615.compiler.frontend.ast.*;
import io.github.tomorrow615.compiler.frontend.parser.Parser;
import io.github.tomorrow615.compiler.frontend.error.Error;
import io.github.tomorrow615.compiler.midend.irgen.IRGenerator;
import io.github.tomorrow615.compiler.util.*;
import io.github.tomorrow615.compiler.frontend.visitor.SemanticVisitor;
import io.github.tomorrow615.compiler.frontend.symbol.SymbolTable;
import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.backend.mips.*;
import io.github.tomorrow615.compiler.backend.codegen.MipsGenerator;
import io.github.tomorrow615.compiler.midend.optimize.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Compiler {
    public static void main(String[] args) {
        String inputFile = "testfile.txt";
        String outputFileError = "error.txt";

        String outputFileLexer = "lexer.txt";
        String outputFileParser = "parser.txt";
        String outputFileSymbol = "symbol.txt";

        String outputFileLlvmIr = "llvm_ir.txt";
        String outputFileLlvmIrOpt = "llvm_ir2.txt";
        String outputFileMips = "mips.txt";

        try {
            String sourceCode = new String(Files.readAllBytes(Paths.get(inputFile)));
            ErrorReporter.clearErrors();

            // --- 步骤 1: 词法分析 ---
            List<Token> tokens;
            try (LexerRecorder lexerRecorder = new LexerRecorder(outputFileLexer)) {
                Lexer lexer = new Lexer(sourceCode, lexerRecorder);
                tokens = lexer.getAllTokens();
            }

            // --- 步骤 2: 语法分析 ---
            CompUnitNode compUnit;
            try (ParserRecorder parserRecorder = new ParserRecorder(outputFileParser)) {
                Parser parser = new Parser(tokens, parserRecorder);
                compUnit = parser.parse();
            }

            // --- 步骤 3: 语义分析 ---
            SemanticVisitor semanticVisitor = new SemanticVisitor();
            semanticVisitor.visit(compUnit);
            List<SymbolTable> allScopes = semanticVisitor.getAllScopes();
            try (SymbolRecorder symbolRecorder = new SymbolRecorder(outputFileSymbol)) {
                symbolRecorder.recordAll(semanticVisitor.getAllScopes());
            }

            // --- 步骤 4: 检查错误 并 输出 ---
            if (ErrorReporter.hasErrors()) {
                try (BufferedWriter errorWriter = new BufferedWriter(new FileWriter(outputFileError))) {
                    for (Error error : ErrorReporter.getErrors()) {
                        errorWriter.write(error.formatForOutput());
                        errorWriter.newLine();
                    }
                }
                return;
            }

            // --- 步骤 5: 基础 IR 生成 和 输出 ---
            IRGenerator irGenerator = new IRGenerator(compUnit, allScopes);
            Module llvmModule = irGenerator.generate();
            try (IRPrinter irPrinter = new IRPrinter(outputFileLlvmIr)) {
                irPrinter.print(llvmModule);
            }

            // --- 步骤 6: LLVM IR 优化（激进内联版） ---
            if (Config.OPTIMIZE_LLVM) {
                // === Phase 1: 内存优化前置 ===
                runPass(llvmModule, new SROA_Simple());
                runPass(llvmModule, new Global2Local());
                runPass(llvmModule, new Mem2Reg());
                runPass(llvmModule, new SimplifyCFG());
                
                // === Phase 2: 洋葱剥皮法 (Onion Peeling) ===
                // 控制递归展开深度，设置合理上限防止 TLE
                int MAX_INLINE_ITERATIONS = 10;
                
                for (int i = 0; i < MAX_INLINE_ITERATIONS; i++) {
                    int before = countModuleInstructions(llvmModule);
                    
                    // 1. 【剥皮】内联一层
                    runPass(llvmModule, new FunctionInlining());
                    
                    // 2. 【清理】内联产生的 alloca 和冗余计算
                    runPass(llvmModule, new SROA_Simple()); // 新增！内联可能暴露新的小数组/结构体
                    runPass(llvmModule, new Mem2Reg());
                    runPass(llvmModule, new GVN());         // 关键！把 fib(5) 折叠成 8，让下一层 fib(8) 能继续内联
                    
                    // 3. 【粉碎】核心清理循环 (SimplifyCFG + DCE + ConstFold)
                    // 必须把 fib(4) 产生的 if(4==1) 分支立刻算死并删掉
                    // 【修复】添加最大迭代限制防止死循环
                    int MAX_INNER_ITERATIONS = 10;
                    for (int j = 0; j < MAX_INNER_ITERATIONS; j++) {
                        int innerBefore = countModuleInstructions(llvmModule);
                        
                        // 常量折叠：算出 4==1 是 false
                        runPass(llvmModule, new ConstantFolding());
                        
                        // 控制流简化：砍掉死分支，删除不可达块
                        runPass(llvmModule, new SimplifyCFG());
                        
                        // 死代码消除
                        runPass(llvmModule, new DeadCodeElimination());
                        
                        // 算术优化（有时候算术也能消掉分支）
                        runPass(llvmModule, new ArithmeticOptimization());
                        
                        int innerAfter = countModuleInstructions(llvmModule);
                        // 收敛则提前退出
                        if (innerBefore == innerAfter) break;
                    }
                    
                    int after = countModuleInstructions(llvmModule);
                    
                    // 收敛检测：无变化则提前退出
                    if (before == after) break;
                    
                    // 【熔断】代码爆炸则强制停止（降低阈值防止 TLE）
                    if (after > 5000) {
                        break;
                    }
                }
                
                // === Phase 3: 收尾优化 ===
                runPass(llvmModule, new AlgebraicSimplification());
                runPass(llvmModule, new ArithmeticOptimization());
                runPass(llvmModule, new LICM());
                runPass(llvmModule, new StrengthReduction());  // 强度削减：乘法转累加
                runPass(llvmModule, new GVN());
                runPass(llvmModule, new SimplifyCFG());
                runPass(llvmModule, new DeadCodeElimination());
                
                // Mem2Reg 后重新编号所有 SSA 值，修复 IR 乱码
                renumberValues(llvmModule);
                
                try (IRPrinter irPrinter = new IRPrinter(outputFileLlvmIrOpt)) {
                    irPrinter.print(llvmModule);
                }
            }

            // --- 步骤 7 & 8: MIPS 生成 ---
            if (Config.GENERATE_MIPS) {
                MipsGenerator mipsGenerator = new MipsGenerator(llvmModule);
                MipsModule mipsModule = mipsGenerator.generate();
                try (MipsPrinter mipsPrinter = new MipsPrinter(outputFileMips)) {
                    mipsPrinter.print(mipsModule);
                }
            }

        } catch (IOException e) {
            System.err.println("文件读写时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 运行单个优化 Pass
     */
    private static void runPass(Module module, Pass pass) {
        pass.runOnModule(module);
    }
    
    /**
     * 统计模块中的总指令数（用于收敛检测）
     */
    private static int countModuleInstructions(Module module) {
        int count = 0;
        for (io.github.tomorrow615.compiler.midend.llvm.value.Function func : module.getFunctions()) {
            if (func.isDeclaration()) continue;
            for (io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock bb : func.getBasicBlocks()) {
                count += bb.getInstructions().size();
            }
        }
        return count;
    }

    /**
     * 为所有无名的 SSA 值分配编号 (修复 Mem2Reg 后的 IR 乱码)
     */
    private static void renumberValues(Module module) {
        for (io.github.tomorrow615.compiler.midend.llvm.value.Function func : module.getFunctions()) {
            if (func.isDeclaration()) continue;
            
            int counter = 0;
            
            // 1. 给参数编号 (强制编号，防止漏网之鱼)
            for (io.github.tomorrow615.compiler.midend.llvm.value.Argument arg : func.getArguments()) {
                arg.setName(String.valueOf(counter++));
            }
            
            // 2. 给基本块和指令编号
            for (io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock bb : func.getBasicBlocks()) {
                // 给基本块编号 (防止 label 名字冲突)
                String bbName = bb.getName();
                if (bbName == null || bbName.isEmpty() || bbName.startsWith("<??")) {
                    bb.setName("label_" + counter++);
                }
                
                for (io.github.tomorrow615.compiler.midend.llvm.instruction.Instruction inst : bb.getInstructions()) {
                    // 只有有返回值的指令才需要名字
                    if (!inst.getType().isVoidType()) {
                        String instName = inst.getName();
                        // 只要名字不对劲，就重命名
                        if (instName == null || instName.isEmpty() || instName.startsWith("<??")) {
                            inst.setName(String.valueOf(counter++));
                        }
                    }
                }
            }
        }
    }
}