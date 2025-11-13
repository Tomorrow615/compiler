package io.github.tomorrow615.compiler.util;

import io.github.tomorrow615.compiler.midend.llvm.Module;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

public class IRPrinter implements AutoCloseable {

    private final PrintWriter writer;

    public IRPrinter(String outputFilename) throws IOException {
        // 遵从你的 Config 开关设计 [cite: 1237-1239, 1241-1243]
        if (Config.ENABLE_LLVM_IR_OUTPUT) {
            this.writer = new PrintWriter(new FileWriter(outputFilename));
        } else {
            this.writer = new PrintWriter(OutputStream.nullOutputStream());
        }
    }

    /**
     * 打印整个 LLVM 模块
     * @param module 内存中的 Module 对象
     */
    public void print(Module module) {
        // 1. 创建 SlotTracker，它会在构造时自动预遍历并命名所有 Value
        SlotTracker tracker = new SlotTracker(module);

        // 2. 调用 Module 的 toString(tracker) 方法，启动打印
        String irCode = module.toString(tracker);

        // 3. 写入文件
        writer.write(irCode);
    }

    @Override
    public void close() {
        writer.close();
    }
}
