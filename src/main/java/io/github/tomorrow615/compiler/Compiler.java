package io.github.tomorrow615.compiler;

import io.github.tomorrow615.compiler.frontend.error.*;
import io.github.tomorrow615.compiler.frontend.lexer.*;
import io.github.tomorrow615.compiler.frontend.ast.*;
import io.github.tomorrow615.compiler.frontend.parser.Parser;
import io.github.tomorrow615.compiler.frontend.error.Error;
import io.github.tomorrow615.compiler.util.*;
import io.github.tomorrow615.compiler.frontend.visitor.SemanticVisitor;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Compiler {
    public static void main(String[] args) {
        String inputFile = "testfile.txt";
        String outputFileLexer = "lexer.txt";
        String outputFileParser = "parser.txt";
        String outputFileSymbol = "symbol.txt";
        String outputFileError = "error.txt";

        try {
            String sourceCode = new String(Files.readAllBytes(Paths.get(inputFile)));
            ErrorReporter.clearErrors(); // 清空上一轮的错误

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
            try (SymbolRecorder symbolRecorder = new SymbolRecorder(outputFileSymbol)) {
                symbolRecorder.recordAll(semanticVisitor.getAllScopes());
            }

            // --- 步骤 4: 检查错误并输出 ---
            if (ErrorReporter.hasErrors()) {
                try (BufferedWriter errorWriter = new BufferedWriter(new FileWriter(outputFileError))) {
                    for (Error error : ErrorReporter.getErrors()) {
                        errorWriter.write(error.formatForOutput());
                        errorWriter.newLine();
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("文件读写时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}