package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.Module;

import java.util.ArrayList;
import java.util.List;

/**
 * Pass 管理器
 * 按顺序执行注册的优化 Pass
 */
public class PassManager {
    private final List<Pass> passes = new ArrayList<>();

    /**
     * 注册一个优化 Pass
     */
    public void addPass(Pass pass) {
        passes.add(pass);
    }

    /**
     * 在模块上运行所有注册的 Pass
     */
    public void runOnModule(Module module) {
        for (Pass pass : passes) {
            pass.runOnModule(module);
        }
    }
}
