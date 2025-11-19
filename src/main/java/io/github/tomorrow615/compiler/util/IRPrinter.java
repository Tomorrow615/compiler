package io.github.tomorrow615.compiler.util;

import io.github.tomorrow615.compiler.midend.llvm.Module;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

public class IRPrinter implements AutoCloseable {
    private final PrintWriter writer;

    public IRPrinter(String outputFilename) throws IOException {
        if (Config.ENABLE_LLVM_IR_OUTPUT) {
            this.writer = new PrintWriter(new FileWriter(outputFilename));
        } else {
            this.writer = new PrintWriter(OutputStream.nullOutputStream());
        }
    }

    public void print(Module module) {
        SlotTracker tracker = new SlotTracker(module);
        String irCode = module.toString(tracker);
        writer.write(irCode);
    }

    @Override
    public void close() {
        writer.close();
    }
}
