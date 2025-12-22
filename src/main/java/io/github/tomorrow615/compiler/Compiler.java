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
                
                // === 阶段一：SSA 构建（核心变革）===
                pm.addPass(new Mem2Reg());  // 内存到寄存器提升，消除 alloca/load/store
                
                // === 阶段二：基于 SSA 的优化（红利期）===
                pm.addPass(new ConstantFolding());         // 常量折叠（效果增强）
                pm.addPass(new AlgebraicSimplification()); // 代数简化 (x+0, x*1 等)
                pm.addPass(new ArithmeticOptimization());  // 乘除法优化 (x*2^k -> x<<k)
                pm.addPass(new CommonSubexprElimination()); // 公共子表达式消除（效果增强）
                pm.addPass(new DeadCodeElimination());     // 死代码删除（效果增强）
                
                pm.runOnModule(llvmModule);
                
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
}