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
    private int outgoingArgsSize;  // 预留给函数调用参数的空间
    private boolean noSaveRa = false;  // [Zombie Stack] main 函数不保存 $ra

    public StackManager(Function function) {
        this.valueOffsetMap = new HashMap<>();
        
        // 1. [核心修改] 扫描所有 Call 指令，计算最大的栈参数空间需求
        int maxCallStackArgs = 0;
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof CallInst call) {
                    // 超过 4 个的参数需要栈传递
                    int argsCount = call.getArguments().size();
                    if (argsCount > 4) {
                        maxCallStackArgs = Math.max(maxCallStackArgs, argsCount - 4);
                    }
                }
            }
        }
        
        // 预留空间：每个参数 4 字节
        int reservedSize = maxCallStackArgs * 4;
        // 保持 8 字节对齐 (MIPS 栈对齐要求)
        if (reservedSize % 8 != 0 && reservedSize > 0) {
            reservedSize += (8 - reservedSize % 8);
        }
        
        // 保存预留给调用参数的空间大小
        this.outgoingArgsSize = reservedSize;

        // 栈从预留区之后开始分配局部变量
        // 0 ~ reservedSize-1 : 留给 call 的参数使用
        // reservedSize ~ ... : 局部变量和当前函数参数
        this.stackSize = reservedSize;

        // 2. 为局部变量 (AllocaInst) 分配空间
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof AllocaInst alloca) {
                    int size = calculateTypeSize(alloca.getAllocatedType());
                    allocSpace(inst, size);
                }
            }
        }

        // 3. 为参数 (Arguments) 分配空间 (Incoming Args)
        // MipsGenerator 会把传入的参数 store 到这些位置
        for (Argument arg : function.getArguments()) {
            allocSpace(arg, 4);
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

    // 分配空间并返回 offset
    public int allocSpace(Value value, int size) {
        // 简单的线性分配：栈底向上增长
        valueOffsetMap.put(value, stackSize);
        int offset = stackSize;
        // 确保 4 字节对齐
        if (size < 4) size = 4;
        stackSize += size;
        return offset;
    }

    // 获取偏移量 (相对于当前 Frame 的底部，不包含动态 Call 区域)
    public int getOffset(Value value) {
        if (!valueOffsetMap.containsKey(value)) {
            throw new RuntimeException("StackManager: Value not allocated in stack (may be VirtualRegister): " + value);
        }
        return valueOffsetMap.get(value);
    }
    
    /**
     * 动态分配溢出槽（由寄存器分配器在溢出时调用）
     * @return 新分配的栈偏移量
     */
    public int allocateSpillSlot() {
        int offset = stackSize;
        stackSize += 4;
        return offset;
    }

    /**
     * 获取 $ra 保存位置的偏移（在所有变量之后）
     */
    public int getRaOffset() {
        return stackSize;
    }

    /**
     * 获取栈帧大小（包含所有变量 + $ra，最小 32 字节，8 字节对齐）
     * [Zombie Stack] 如果 noSaveRa 为 true 且栈上没有任何数据，返回 0
     */
    public int getFrameSize() {
        // [Zombie Stack] 如果不需要保存 $ra，且栈上没有任何数据 (stackSize == 0)
        // 则返回 0，完全消除栈帧
        if (noSaveRa && stackSize == 0) {
            return 0;
        }
        
        int size = stackSize;
        if (!noSaveRa) {
            size += 4; // +4 for $ra
        }
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
    
    /**
     * 获取预留给函数调用参数（第5个及以后）的空间大小
     * 这部分空间的偏移在扩展栈帧时不应被修改
     */
    public int getOutgoingArgsSize() {
        return outgoingArgsSize;
    }
    
    /**
     * [Zombie Stack] 设置是否跳过 $ra 保存
     * 适用于 main 函数（通过 syscall 10 退出，无需返回）
     */
    public void setNoSaveRa(boolean noSaveRa) {
        this.noSaveRa = noSaveRa;
    }
}