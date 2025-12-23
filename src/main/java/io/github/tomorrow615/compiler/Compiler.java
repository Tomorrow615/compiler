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

            // --- 步骤 6: LLVM IR 优化 ---
            if (Config.OPTIMIZE_LLVM) {
                PassManager pm = new PassManager();
                // === 内存优化前置准备 ===
                pm.addPass(new SROA_Simple());             // 1. 拆解数组
                pm.addPass(new Global2Local());            // 2. 全局变量本地化
                
                // === 核心 SSA 构造 ===
                pm.addPass(new Mem2Reg());                 // 3. 提升到寄存器
                pm.addPass(new SimplifyCFG());             // 4. CFG 简化（含分支折叠）
                
                // === 函数内联 [Phase 1.5] ===
                pm.addPass(new FunctionInlining());        // 5. 函数内联
                pm.addPass(new Mem2Reg());                 // 6. 内联后再次 Mem2Reg（处理新 alloca）
                pm.addPass(new SimplifyCFG());             // 7. 内联后 CFG 清理
                
                // === 循环优化 ===
                pm.addPass(new LICM());                    // 8. 循环不变量外提 [Phase 1.3]
                
                // === 迭代优化 ===
                pm.addPass(new ConstantFolding());         // 9. 常量折叠
                pm.addPass(new DeadCodeElimination());     // 10. 死代码消除
                pm.addPass(new AlgebraicSimplification()); // 11. 代数简化
                pm.addPass(new ArithmeticOptimization());  // 12. 乘除优化
                pm.addPass(new GVN());                     // 13. 全局值编号 [Phase 1.4]


                
                // === 最终清理 ===
                pm.addPass(new SimplifyCFG());             // 10. 再次 CFG 清理
                pm.addPass(new DeadCodeElimination());     // 11. 最终死代码清理
                pm.runOnModule(llvmModule);
                
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