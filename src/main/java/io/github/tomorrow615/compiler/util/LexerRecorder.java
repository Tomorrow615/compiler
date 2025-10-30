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
            this.writer = new PrintWriter(new FileWriter(outputFilename));
        } else {
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
