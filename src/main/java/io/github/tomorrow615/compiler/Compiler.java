package io.github.tomorrow615.compiler;

import io.github.tomorrow615.compiler.frontend.error.Error;
import io.github.tomorrow615.compiler.frontend.error.ErrorReporter;
import io.github.tomorrow615.compiler.frontend.lexer.Lexer;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import io.github.tomorrow615.compiler.util.Config;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Compiler {
    public static void main(String[] args) {
        // 定义输入/输出文件名
        String inputFile = "testfile.txt";
        String outputFileLexer = "lexer.txt";
        String outputFileError = "error.txt";

        try {
            // --- 步骤 1: 直接按相对路径读取源文件 ---
            // 这会读取与程序运行目录同级的 testfile.txt
            String sourceCode = new String(Files.readAllBytes(Paths.get(inputFile)));

            // --- 步骤 2: 调用词法分析器 ---
            Lexer lexer = new Lexer(sourceCode);
            List<Token> tokens = lexer.getAllTokens();

            // --- 步骤 3: 根据分析结果生成输出文件 ---
            if (ErrorReporter.hasErrors()) {
                // 如果有错误，则输出到 error.txt
                List<Error> errors = ErrorReporter.getErrors();
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileError))) {
                    for (Error error : errors) {
                        writer.write(error.formatForOutput());
                        writer.newLine();
                    }
                }
            } else {
                // 如果没有错误，并且输出开关是打开的，则输出到 lexer.txt
                if (Config.ENABLE_LEXER_OUTPUT) {
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileLexer))) {
                        // 循环不包含最后一个 EOF Token
                        for (int i = 0; i < tokens.size() - 1; i++) {
                            writer.write(tokens.get(i).formatForOutput());
                            writer.newLine();
                        }
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