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
     * 采用 Spill Everything 策略：为所有虚拟寄存器分配栈空间
     */
    private void analyze(Function function) {
        // 1. 扫描所有 Call 指令，确定 Outgoing Args 区域的大小
        int maxCallArgs = 0;
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof CallInst callInst) {
                    maxCallArgs = Math.max(maxCallArgs, callInst.getArguments().size());
                }
            }
        }

        // 基础偏移量从 Outgoing Args 之后开始
        int currentOffset = maxCallArgs * 4;

        // 2. 为当前函数的参数 (Arguments) 分配空间
        for (Argument arg : function.getArguments()) {
            valueOffsetMap.put(arg, currentOffset);
            currentOffset += 4;
        }

        // 3. 遍历所有指令，为有返回值的指令分配空间
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                // void 类型指令不需要分配空间
                if (inst.getType().isVoidType()) {
                    continue;
                }

                // 计算需要的大小
                int size = 4; // 默认 4 字节（适用于所有虚拟寄存器）

                if (inst instanceof AllocaInst allocaInst) {
                    // 特殊处理：Alloca 数组需要更大空间
                    var allocatedType = allocaInst.getAllocatedType();
                    if (allocatedType.isArrayType()) {
                        var arrayType = (io.github.tomorrow615.compiler.midend.llvm.type.ArrayType) allocatedType;
                        size = arrayType.getNumElements() * 4;
                    } else {
                        size = 4;
                    }
                }
                // 其他所有指令（Add, Sub, Load, Move 等）统一 4 字节

                valueOffsetMap.put(inst, currentOffset);
                currentOffset += size;
            }
        }

        // 4. 为 $ra 留一个位置
        this.raOffset = currentOffset;
        currentOffset += 4;

        // 5. 确定最终 FrameSize (保持 8 字节对齐)
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