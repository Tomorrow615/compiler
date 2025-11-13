package io.github.tomorrow615.compiler.util;

import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.value.*;
import io.github.tomorrow615.compiler.midend.llvm.instruction.Instruction;

import java.util.HashMap;
import java.util.Map;

/**
 * 遍历 Module，为所有需要命名的 Value 分配一个唯一的文本名称。
 *
 */
public class SlotTracker {
    // 存储 Value 对象到其最终 String 名字的映射
    private final Map<Value, String> nameMap;
    // 存储每个 Function 内部的局部变量计数器
    private final Map<Function, Integer> funcValueCounters;

    public SlotTracker(Module module) {
        this.nameMap = new HashMap<>();
        this.funcValueCounters = new HashMap<>();
        traceModule(module);
    }

    /**
     * 预遍历整个模块，填充 nameMap
     */
    private void traceModule(Module module) {
        // 1. 遍历全局变量
        for (GlobalVariable gv : module.getGlobalVariables()) {
            // GlobalVariable 的 name 在构造时已经设置 (e.g., "@g")
            nameMap.put(gv, gv.getName());
        }

        // 2. 遍历函数
        for (Function func : module.getFunctions()) {
            // Function 的 name 在构造时已经设置 (e.g., "@main")
            nameMap.put(func, func.getName());
            traceFunction(func);
        }
    }

    /**
     * 预遍历单个函数，为所有局部 Value (Argument, BasicBlock, Instruction) 命名
     */
    private void traceFunction(Function func) {

        // --- [ 修复开始 ] ---
        // 1. 为参数命名 (e.g., %0, %1)
        //    (我们必须在 `isDeclaration` 检查*之前*执行此操作)
        int counter = 0;
        for (Argument arg : func.getArguments()) {
            String name = "%" + counter++;
            arg.setName(name); // <-- 我们直接更新 Argument 对象的名字
            nameMap.put(arg, name);
        }

        if (func.isDeclaration()) {
            return; // 声明 (declare) 没有 BB 和指令
        }
        // --- [ 修复结束 ] ---

        // 2. 为基本块和指令命名 (这段逻辑不变，只是 counter 初始化移上去了)
        for (BasicBlock bb : func.getBasicBlocks()) {
            nameMap.put(bb, bb.getName());
            for (Instruction inst : bb.getInstructions()) {
                if (!inst.getType().isVoidType()) {
                    String name = "%" + counter++;
                    inst.setName(name);
                    nameMap.put(inst, name);
                }
            }
        }
        funcValueCounters.put(func, counter);
    }

    /**
     * IRPrinter 调用的核心方法：获取一个 Value 对应的打印名称
     */
    public String getName(Value val) {
        if (val instanceof ConstantInt) {
            // 常量直接打印其值 [cite: 1484-1493]
            return val.toString();
        }
        if (!nameMap.containsKey(val)) {
            // 这是一个不应该发生的情况，说明 trace 阶段漏了
            // 但作为健壮性
            return "<??" + val.hashCode() + "??>";
        }
        return nameMap.get(val);
    }
}
