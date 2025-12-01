package io.github.tomorrow615.compiler.util;

import io.github.tomorrow615.compiler.backend.mips.MipsModule;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

public class MipsPrinter implements AutoCloseable {
    private final PrintWriter writer;

    public MipsPrinter(String outputFilename) throws IOException {
        // 只要开启了生成 MIPS，就创建文件写入流
        if (Config.GENERATE_MIPS) {
            this.writer = new PrintWriter(new FileWriter(outputFilename));
        } else {
            this.writer = new PrintWriter(OutputStream.nullOutputStream());
        }
    }

    public void print(MipsModule module) {
        if (module != null) {
            writer.write(module.toString());
        }
    }

    @Override
    public void close() {
        writer.close();
    }
}