package io.github.tomorrow615.compiler;

import io.github.tomorrow615.compiler.frontend.error.Error;
import io.github.tomorrow615.compiler.frontend.error.ErrorReporter;
import io.github.tomorrow615.compiler.frontend.lexer.Lexer;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import io.github.tomorrow615.compiler.frontend.ast.*;
import io.github.tomorrow615.compiler.util.Config;
import io.github.tomorrow615.compiler.frontend.parser.Parser;
import io.github.tomorrow615.compiler.frontend.visitor.ASTPrinter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Compiler {
    public static void main(String[] args) {
        // 定义输入/输出文件名
        String inputFile = "testfile.txt";
        String outputFileLexer = "lexer.txt";
        String outputFileParser = "parser.txt";
        String outputFileError = "error.txt";

        try {
            String sourceCode = new String(Files.readAllBytes(Paths.get(inputFile)));

            // --- 步骤 1: 词法分析 ---
            Lexer lexer = new Lexer(sourceCode);
            List<Token> tokens = lexer.getAllTokens();

            // --- 步骤 2: 语法分析 ---
            Parser parser = new Parser(tokens);
            CompUnitNode astRoot = parser.parse();

            // --- 步骤 3: 检查错误并生成输出 ---
            if (ErrorReporter.hasErrors()) {
                List<Error> errors = ErrorReporter.getErrors();
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileError))) {
                    for (Error error : errors) {
                        writer.write(error.formatForOutput());
                        writer.newLine();
                    }
                }
            } else {
                if (Config.ENABLE_LEXER_OUTPUT) {
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileLexer))) {
                        for (int i = 0; i < tokens.size() - 1; i++) {
                            writer.write(tokens.get(i).formatForOutput());
                            writer.newLine();
                        }
                    }
                }
                if (Config.ENABLE_PARSER_OUTPUT) {
                    try (PrintWriter writer = new PrintWriter(new FileWriter(outputFileParser))) {
                        ASTPrinter printer = new ASTPrinter(writer);
                        printer.print(astRoot);
                    }
                }
            }
        } catch (IOException e) {
            // 统一处理所有可能的文件读写异常
            System.err.println("文件读写时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}