package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;

/**
 * 优化 Pass 接口
 * 所有优化算法都实现此接口
 */
public interface Pass {
    /**
     * 获取 Pass 名称（用于调试输出）
     */
    String getName();

    /**
     * 在整个模块上运行优化
     */
    default void runOnModule(Module module) {
        for (Function func : module.getFunctions()) {
            if (!func.isDeclaration()) {
                runOnFunction(func);
            }
        }
    }

    /**
     * 在单个函数上运行优化
     */
    void runOnFunction(Function function);
}
