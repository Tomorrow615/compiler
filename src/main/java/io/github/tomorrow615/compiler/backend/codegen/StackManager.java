package io.github.tomorrow615.compiler.backend.codegen;

import io.github.tomorrow615.compiler.midend.llvm.type.VoidType;
import io.github.tomorrow615.compiler.midend.llvm.type.ArrayType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.Argument;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;
import io.github.tomorrow615.compiler.midend.llvm.instruction.Instruction;
import io.github.tomorrow615.compiler.midend.llvm.instruction.AllocaInst;
import io.github.tomorrow615.compiler.midend.llvm.instruction.PhiInst;
import io.github.tomorrow615.compiler.midend.llvm.instruction.CallInst;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;

import java.util.HashMap;
import java.util.Map;

/**
 * 栈帧管理器 (全栈式分配策略)
 * 
 * 栈帧布局 (从低地址到高地址):
 * [0, bottomReserve):       Phi 临时 + 调用参数预留区 (processPhiNodes 使用)
 * [bottomReserve, ...):     函数参数 + 非 void 指令结果 + Alloca 数组
 * [stackSize]:              $ra 保存位置
 */
public class StackManager {
    private final Map<Value, Integer> valueOffsetMap;
    private int stackSize;
    private int bottomReserve;  // 栈底预留空间

    public StackManager(Function function) {
        this.valueOffsetMap = new HashMap<>();
        
        // 1. 计算栈底预留空间 (用于 Phi 临时存储 和 调用参数传递)
        int maxPhiCount = 0;
        int maxCallArgs = 0;
        for (BasicBlock bb : function.getBasicBlocks()) {
            int phiCount = 0;
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof PhiInst) {
                    phiCount++;
                } else if (inst instanceof CallInst call) {
                    // 调用参数数量 (超过4个的需要栈传递)
                    int argCount = call.getOperands().size() - 1; // -1 去掉 callee
                    if (argCount > 4) {
                        maxCallArgs = Math.max(maxCallArgs, argCount - 4);
                    }
                }
            }
            maxPhiCount = Math.max(maxPhiCount, phiCount);
        }
        
        // 预留空间：max(Phi数量, 超过4个的调用参数, 4) * 4 字节
        this.bottomReserve = Math.max(Math.max(maxPhiCount, maxCallArgs), 4) * 4;
        this.stackSize = bottomReserve;  // 从预留区之后开始分配
        
        // 2. 为函数参数分配栈空间
        for (Argument arg : function.getArguments()) {
            allocSpace(arg, 4);
        }

        // 3. 为所有非 Void 指令分配栈空间
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (!(inst.getType() instanceof VoidType)) {
                    // AllocaInst 需要分配其 allocatedType 的大小
                    if (inst instanceof AllocaInst alloca) {
                        int size = calculateTypeSize(alloca.getAllocatedType());
                        allocSpace(inst, size);
                    } else {
                        allocSpace(inst, 4);
                    }
                }
            }
        }
    }

    /**
     * 计算类型占用的字节数
     */
    private int calculateTypeSize(Type type) {
        if (type instanceof ArrayType arrayType) {
            int elementSize = calculateTypeSize(arrayType.getElementType());
            return arrayType.getNumElements() * elementSize;
        }
        // 基本类型 (i32, i1, pointer) 都当作 4 字节
        return 4;
    }

    private void allocSpace(Value value, int size) {
        this.valueOffsetMap.put(value, stackSize);
        // 确保 4 字节对齐
        if (size < 4) size = 4;
        stackSize += size;
    }

    public int getOffset(Value value) {
        if (!valueOffsetMap.containsKey(value)) {
            throw new RuntimeException("StackManager: Value not allocated in stack: " + value);
        }
        return valueOffsetMap.get(value);
    }

    /**
     * 获取 $ra 保存位置的偏移（在所有变量之后）
     */
    public int getRaOffset() {
        return stackSize;
    }

    /**
     * 获取栈帧大小（包含所有变量 + $ra，最小 32 字节，8 字节对齐）
     */
    public int getFrameSize() {
        int size = stackSize + 4; // +4 for $ra
        size = Math.max(size, 32);
        // 8 字节对齐
        if (size % 8 != 0) {
            size += (8 - size % 8);
        }
        return size;
    }

    /**
     * 获取栈底预留空间大小 (用于 processPhiNodes 的临时存储)
     */
    public int getBottomReserve() {
        return bottomReserve;
    }
}