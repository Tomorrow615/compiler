package io.github.tomorrow615.compiler.util;

import io.github.tomorrow615.compiler.frontend.lexer.*;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Set;

public class ParserRecorder implements AutoCloseable {
    private final PrintWriter writer;
    private static final Set<String> DONT_PRINT = Set.of("BlockItem", "Decl", "BType");

    public ParserRecorder(String outputFilename) throws IOException {
        if (Config.ENABLE_PARSER_OUTPUT) {
            this.writer = new PrintWriter(new FileWriter(outputFilename));
        } else {
            this.writer = new PrintWriter(OutputStream.nullOutputStream());
        }
    }

    public void recordToken(Token token) {
        if (token != null && token.getType() != TokenType.EOF) {
            writer.println(token.getType() + " " + token.getText());
        }
    }

    public void recordSyntax(String componentName) {
        if (!DONT_PRINT.contains(componentName)) {
            writer.println("<" + componentName.replace("Node", "") + ">");
        }
    }

    @Override
    public void close() {
        writer.close();
    }
}
