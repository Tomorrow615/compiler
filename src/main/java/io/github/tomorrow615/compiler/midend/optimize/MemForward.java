package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.*;

/**
 * 内存转发优化 Pass (Memory Forwarding)
 * 
 * 功能：
 * 1. Store-Load 转发：store 后紧跟的同地址 load 直接用 store 的值替换
 * 2. Load-Load 消除：同一基本块内对同一地址的重复 load 复用第一次的结果
 * 
 * 场景：
 * - 数组初始化后立即读取
 * - 临时变量的频繁读写
 * - Mem2Reg 无法处理的数组/GEP 地址
 */
public class MemForward implements Pass {

    @Override
    public String getName() {
        return "MemForward";
    }

    @Override
    public void runOnFunction(Function function) {
        for (BasicBlock bb : function.getBasicBlocks()) {
            optimizeBlock(bb);
        }
    }

    private void optimizeBlock(BasicBlock bb) {
        // 地址 -> 最近存储/加载的值
        Map<Value, Value> memoryState = new HashMap<>();
        
        // 收集需要移除的指令，避免迭代时修改
        List<Instruction> toRemove = new ArrayList<>();
        
        // 第一次遍历：识别冗余 Load
        for (Instruction inst : bb.getInstructions()) {
            if (inst instanceof StoreInst store) {
                Value address = store.getPointer();
                Value storedValue = store.getValue();
                
                // 记录这个地址的最新存储值
                memoryState.put(address, storedValue);
                
            } else if (inst instanceof LoadInst load) {
                Value address = load.getPointer();
                Value cachedValue = memoryState.get(address);
                
                if (cachedValue != null) {
                    // 命中回传！用缓存值替换所有使用
                    load.replaceAllUsesWith(cachedValue);
                    toRemove.add(load);
                } else {
                    // 记录 load 结果，用于后续 load-load 消除
                    memoryState.put(address, load);
                }
                
            } else if (inst instanceof CallInst) {
                // 函数调用可能会修改内存，保守清空状态
                memoryState.clear();
                
            } else if (mayWriteMemory(inst)) {
                memoryState.clear();
            }
        }
        
        // 执行真正的指令删除
        for (Instruction inst : toRemove) {
            inst.remove();
        }
    }
    
    /**
     * 判断指令是否可能写内存（除了显式的 StoreInst 和 CallInst）
     */
    private boolean mayWriteMemory(Instruction inst) {
        // 目前 IR 中只有 StoreInst 和 CallInst 会写内存
        return false;
    }
}
