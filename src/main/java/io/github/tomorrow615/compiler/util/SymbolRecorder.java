package io.github.tomorrow615.compiler.util;

import io.github.tomorrow615.compiler.frontend.symbol.*;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;

public class SymbolRecorder implements AutoCloseable {
    private final PrintWriter writer;

    public SymbolRecorder(String outputFilename) throws IOException {
        if (Config.ENABLE_SYMBOL_OUTPUT) {
            this.writer = new PrintWriter(new FileWriter(outputFilename));
        } else {
            this.writer = new PrintWriter(OutputStream.nullOutputStream());
        }
    }

    public void recordAll(List<SymbolTable> scopes) {
        if (scopes == null) {
            return;
        }

        for (SymbolTable scope : scopes) {
            int scopeId = scope.getScopeId();
            for (Symbol symbol : scope.getOrderedSymbols()) {
                // 输出格式："作用域序号 单词字符串 类型名称"
                String line = scopeId + " " + symbol.getName() + " " + symbol.getType().toString();
                writer.println(line);
            }
        }
    }

    @Override
    public void close() {
        writer.close();
    }
}
