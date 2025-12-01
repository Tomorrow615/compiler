package io.github.tomorrow615.compiler.backend.codegen;

import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.value.Argument;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.midend.llvm.instruction.AllocaInst;
import io.github.tomorrow615.compiler.midend.llvm.instruction.CallInst;
import io.github.tomorrow615.compiler.midend.llvm.instruction.Instruction;

import java.util.HashMap;
import java.util.Map;

/**
 * 栈帧管理器
 * 负责为函数内的每个 Value 分配栈上的偏移量
 * 采用全栈式分配策略 (All-Stack Allocation)
 */
public class StackManager {
    // 记录每个 Value 相对于 $sp 的偏移量
    private final Map<Value, Integer> valueOffsetMap;

    // 总栈帧大小
    private int frameSize;

    // $ra 寄存器的保存位置偏移
    private int raOffset;

    public StackManager(Function function) {
        this.valueOffsetMap = new HashMap<>();
        this.frameSize = 0;

        // 在构造时立即进行分析
        analyze(function);
    }

    /**
     * 核心分析逻辑：遍历函数，分配空间
     */
    private void analyze(Function function) {
        // 1. 扫描所有 Call 指令，确定 Outgoing Args 区域的大小
        // MIPS 规范：必须为调用的子函数预留参数空间（即使 < 4个）
        // 这里简化：只计算参数个数，每个预留 4 字节
        int maxCallArgs = 0;
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof CallInst callInst) {
                    maxCallArgs = Math.max(maxCallArgs, callInst.getArguments().size());
                }
            }
        }

        // 基础偏移量从 Outgoing Args 之后开始
        // 即使没有 call，MIPS 有时也建议保留 16 字节(4 args)，但 Phase 1 我们按需分配即可
        int currentOffset = maxCallArgs * 4;

        // 2. 为当前函数的参数 (Arguments) 分配空间
        // 策略：虽然前4个在寄存器，但我们在 Prologue 会把它们存入栈，统一变成栈上变量处理
        for (Argument arg : function.getArguments()) {
            valueOffsetMap.put(arg, currentOffset);
            currentOffset += 4;
        }

        // 3. 遍历所有指令，为有返回值的指令分配空间
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                // void 类型指令不需要分配空间 (如 store, br, ret void, call void)
                if (inst.getType().isVoidType()) {
                    continue;
                }

                // 计算需要的大小
                int size = 4; // 默认 4 字节

                if (inst instanceof AllocaInst allocaInst) {
                    // 如果是 Alloca，也就是局部变量/数组定义
                    // 需要根据分配的类型计算大小
                    // 指针指向的类型 -> 获取该类型大小
                    // 这里假设 Phase 1 只有 int 或 int 数组
                    // PointerType -> TargetType
                    // 你的 AllocaInst 存的是 allocatedType
                    var allocatedType = allocaInst.getAllocatedType();
                    if (allocatedType.isArrayType()) {
                        // 数组大小：元素个数 * 4
                        // 注意：这里需要你确认 ArrayType 有 getNumElements() 方法
                        // 且 Phase 1 假设都是 int 数组
                        var arrayType = (io.github.tomorrow615.compiler.midend.llvm.type.ArrayType) allocatedType;
                        size = arrayType.getNumElements() * 4;
                    } else {
                        // 普通 int 变量
                        size = 4;
                    }
                }

                valueOffsetMap.put(inst, currentOffset);
                currentOffset += size;
            }
        }

        // 4. 最后为 $ra 留一个位置
        this.raOffset = currentOffset;
        currentOffset += 4;

        // 5. 确定最终 FrameSize (保持 8 字节对齐是好习惯，虽然 Phase 1 不强求)
        this.frameSize = currentOffset;
        if (this.frameSize % 8 != 0) {
            this.frameSize += 4;
        }
    }

    /**
     * 获取 Value 在栈上的偏移量
     */
    public int getOffset(Value value) {
        if (!valueOffsetMap.containsKey(value)) {
            // 如果查不到，可能是代码生成逻辑有误，或者该 Value 是常量/全局变量
            // 常量和全局变量不应该查 StackManager，而应该直接生成 li/la
            throw new RuntimeException("StackManager: Value not found in stack frame -> " + value.getName());
        }
        return valueOffsetMap.get(value);
    }

    public int getFrameSize() {
        return frameSize;
    }

    public int getRaOffset() {
        return raOffset;
    }
}