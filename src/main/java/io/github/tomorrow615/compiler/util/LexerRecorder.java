package io.github.tomorrow615.compiler.util;

import io.github.tomorrow615.compiler.frontend.lexer.*;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

public class LexerRecorder implements AutoCloseable {
    private final PrintWriter writer;

    public LexerRecorder(String outputFilename) throws IOException {
        if (Config.ENABLE_LEXER_OUTPUT) {
            // 如果开关打开，创建真正的文件写入器。
            this.writer = new PrintWriter(new FileWriter(outputFilename));
        } else {
            // 如果开关关闭，创建“黑洞”写入器，它会丢弃所有输出。
            this.writer = new PrintWriter(OutputStream.nullOutputStream());
        }
    }

    public void recordToken(Token token) {
        if (token != null && token.getType() != TokenType.EOF) {
            writer.println(token.formatForOutput());
        }
    }

    @Override
    public void close() {
        writer.close();
    }
}
