package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.type.IntegerType;
import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.value.*;
import java.util.*;

/**
 * 全局变量本地化 Pass
 * 针对叶子函数（不调用其他函数的函数），将全局变量缓存到局部变量中。
 * 结合 Mem2Reg，可以将全局变量操作提升到寄存器，大幅减少 MEM 开销。
 */
public class Global2Local implements Pass {

    @Override
    public String getName() {
        return "Global2Local";
    }

    @Override
    public void runOnFunction(Function func) {
        // 1. 安全性检查：必须是叶子函数（不含 CallInst）
        if (!isLeafFunction(func) || func.getBasicBlocks().isEmpty()) {
            return;
        }

        BasicBlock entry = func.getBasicBlocks().get(0);
        
        // 2. 收集函数内使用的全局变量，并标记是否被修改 (dirty)
        Map<GlobalVariable, Boolean> usedGlobals = new HashMap<>();

        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof LoadInst load && load.getPointer() instanceof GlobalVariable gv) {
                    if (isI32Pointer(gv.getType())) {
                        usedGlobals.putIfAbsent(gv, false); // 默认不脏
                    }
                } else if (inst instanceof StoreInst store && store.getPointer() instanceof GlobalVariable gv) {
                    if (isI32Pointer(gv.getType())) {
                        usedGlobals.put(gv, true); // 标记为脏（被修改）
                    }
                }
            }
        }

        if (usedGlobals.isEmpty()) return;

        // 3. 开始转换
        for (Map.Entry<GlobalVariable, Boolean> entrySet : usedGlobals.entrySet()) {
            localizeGlobal(func, entry, entrySet.getKey(), entrySet.getValue());
        }
    }
    
    /**
     * 辅助检查：是否为 i32* 类型
     */
    private boolean isI32Pointer(io.github.tomorrow615.compiler.midend.llvm.type.Type type) {
        if (type instanceof PointerType pt) {
            return pt.getTargetType().equals(IntegerType.i32);
        }
        return false;
    }

    private boolean isLeafFunction(Function func) {
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof CallInst) return false;
            }
        }
        return true;
    }

    private void localizeGlobal(Function func, BasicBlock entry, GlobalVariable gv, boolean isDirty) {
        // 1. 在 Entry 块开头创建局部 alloca
        AllocaInst localAlloca = new AllocaInst(IntegerType.i32, gv.getName() + "_local");
        localAlloca.setParentBlock(entry);
        entry.getInstructions().add(0, localAlloca);

        // 2. 在 alloca 之后立即 load 全局变量的值（初始化）
        LoadInst initLoad = new LoadInst(gv, "init_" + gv.getName(), null);
        initLoad.setParentBlock(entry);
        entry.getInstructions().add(1, initLoad);

        StoreInst initStore = new StoreInst(initLoad, localAlloca, null);
        initStore.setParentBlock(entry);
        entry.getInstructions().add(2, initStore);

        // 3. 替换函数体内的所有引用
        for (BasicBlock bb : func.getBasicBlocks()) {
            List<Instruction> instructions = new ArrayList<>(bb.getInstructions());
            for (Instruction inst : instructions) {
                // 跳过我们刚生成的初始化指令
                if (inst == initLoad || inst == initStore) continue;

                if (inst instanceof LoadInst load && load.getPointer() == gv) {
                    load.setOperand(0, localAlloca);
                } else if (inst instanceof StoreInst store && store.getPointer() == gv) {
                    store.setOperand(1, localAlloca);
                }
            }
        }

        // 4. 写回：只有当变量被修改过 (isDirty) 时才写回
        if (isDirty) {
            for (BasicBlock bb : func.getBasicBlocks()) {
                if (bb.getInstructions().isEmpty()) continue;
                Instruction terminator = bb.getInstructions().get(bb.getInstructions().size() - 1);
                
                if (terminator instanceof ReturnInst) {
                    LoadInst finalLoad = new LoadInst(localAlloca, "final_" + gv.getName(), null);
                    finalLoad.setParentBlock(bb);
                    
                    StoreInst finalStore = new StoreInst(finalLoad, gv, null);
                    finalStore.setParentBlock(bb);

                    int retIdx = bb.getInstructions().indexOf(terminator);
                    bb.getInstructions().add(retIdx, finalLoad);
                    bb.getInstructions().add(retIdx + 1, finalStore);
                }
            }
        }
    }
}
