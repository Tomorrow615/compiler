package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.type.ArrayType;
import io.github.tomorrow615.compiler.midend.llvm.type.IntegerType;
import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.*;
import io.github.tomorrow615.compiler.util.Config;

import java.util.*;

/**
 * 全局变量本地化 Pass
 * 
 * 分为两种模式：
 * 1. 基本模式 (OPT_GLOBAL2LOCAL): 针对叶子函数的 i32 标量全局变量本地化
 * 2. 激进模式 (AGGRESSIVE_MODE): 额外支持 main 函数的数组全局变量本地化
 */
public class Global2Local implements Pass {

    private static final int MAX_ARRAY_SIZE = 64; // 数组大小限制（激进模式）

    @Override
    public String getName() {
        return "Global2Local";
    }

    @Override
    public void runOnFunction(Function func) {
        if (func.getBasicBlocks().isEmpty()) return;

        BasicBlock entry = func.getBasicBlocks().get(0);
        
        // 收集函数内使用的全局变量，并标记是否被修改 (dirty)
        Map<GlobalVariable, Boolean> candidates = new HashMap<>();

        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof LoadInst load && load.getPointer() instanceof GlobalVariable gv) {
                    candidates.putIfAbsent(gv, false);
                } else if (inst instanceof StoreInst store && store.getPointer() instanceof GlobalVariable gv) {
                    candidates.put(gv, true);
                } else if (inst instanceof GetElementPtrInst gep && gep.getOperand(0) instanceof GlobalVariable gv) {
                    candidates.putIfAbsent(gv, false);
                    for (Use u : gep.getUsers()) {
                        if (u.getUser() instanceof StoreInst s && s.getPointer() == gep) {
                            candidates.put(gv, true);
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) return;

        // 筛选符合条件的全局变量
        List<GlobalVariable> toLocalize = new ArrayList<>();
        for (GlobalVariable gv : candidates.keySet()) {
            if (canLocalize(gv, func)) {
                toLocalize.add(gv);
            }
        }

        // 执行转换
        for (GlobalVariable gv : toLocalize) {
            localizeGlobal(func, entry, gv, candidates.get(gv));
        }
    }

    /**
     * 判断全局变量是否可以本地化
     * 
     * 分两种情况：
     * 1. 基本模式：叶子函数 + i32 标量（受 OPT_GLOBAL2LOCAL 控制）
     * 2. 激进模式：main 函数 + i32 标量/数组（受 AGGRESSIVE_MODE 控制）
     */
    private boolean canLocalize(GlobalVariable gv, Function func) {
        Type type = gv.getType();
        if (!(type instanceof PointerType pt)) return false;
        Type valType = pt.getTargetType();

        boolean isScalar = valType.equals(IntegerType.i32);
        boolean isArray = valType instanceof ArrayType at
            && at.getElementType().equals(IntegerType.i32)
            && at.getNumElements() <= MAX_ARRAY_SIZE;

        // 逃逸分析：所有 Use 必须在当前函数内，且数组地址不能被"传递"出去
        for (Use use : gv.getUsers()) {
            User user = use.getUser();
            if (user instanceof Instruction inst) {
                if (inst.getParentBlock().getParentFunction() != func) {
                    return false;
                }
                
                // [Critical Fix] 检查 GEP 结果是否被存储（作为指针传递）
                // 如果 gep(@a, 0, 0) 的结果被 store 到另一个 alloca，
                // 说明数组地址被当作指针传递，本地化后语义会出错
                if (inst instanceof GetElementPtrInst gep) {
                    for (Use gepUse : gep.getUsers()) {
                        User gepUser = gepUse.getUser();
                        // GEP 结果只能用于直接的 Load/Store 数组元素
                        // 如果被 Store 的是 GEP 指针本身（而不是通过 GEP 访问后的值），说明逃逸
                        if (gepUser instanceof StoreInst storeInst) {
                            // 检查是 store 值到 GEP 位置，还是 store GEP 指针到其他位置
                            if (storeInst.getValue() == gep) {
                                // GEP 指针本身被存储 → 数组地址逃逸！
                                return false;
                            }
                            // store xxx, gep 是正常的元素访问，OK
                        } else if (!(gepUser instanceof LoadInst)) {
                            // GEP 结果被用于其他操作（如 CallInst 参数），逃逸
                            return false;
                        }
                    }
                }
            } else {
                return false;
            }
        }

        // === 基本模式：叶子函数 + i32 标量 ===
        // 条件安全：叶子函数不会调用其他函数，全局变量不会被外部观察
        if (Config.OPT_GLOBAL2LOCAL && isScalar && isLeafFunction(func)) {
            return true;
        }

        // === 激进模式：main 函数 + i32 标量/数组 ===
        // 条件：仅 main 函数（内联后的大 main），配合 SROA/Mem2Reg
        if (Config.AGGRESSIVE_MODE && func.getName().equals("@main")) {
            return isScalar || isArray;
        }

        return false;
    }

    /**
     * 检查是否为叶子函数（不含 CallInst）
     */
    private boolean isLeafFunction(Function func) {
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof CallInst) return false;
            }
        }
        return true;
    }

    private void localizeGlobal(Function func, BasicBlock entry, GlobalVariable gv, boolean isDirty) {
        Type valType = ((PointerType) gv.getType()).getTargetType();
        boolean isArray = valType.isArrayType();
        int arraySize = isArray ? ((ArrayType) valType).getNumElements() : 1;

        // 1. 在 Entry 块开头创建局部 alloca
        AllocaInst localAlloca = new AllocaInst(valType, gv.getName() + "_local");
        localAlloca.setParentBlock(entry);
        entry.getInstructions().add(0, localAlloca);

        // 2. 初始化
        int insertIdx = 0;
        while (insertIdx < entry.getInstructions().size() && entry.getInstructions().get(insertIdx) instanceof AllocaInst) {
            insertIdx++;
        }

        if (!isArray) {
            // --- 标量处理 ---
            // 【关键】先替换现有引用，再创建初始化代码
            replaceUsers(func, gv, localAlloca);
            
            LoadInst load = new LoadInst(gv, gv.getName() + "_init", null);
            load.setParentBlock(entry);
            entry.getInstructions().add(insertIdx++, load);

            StoreInst store = new StoreInst(load, localAlloca, null);
            store.setParentBlock(entry);
            entry.getInstructions().add(insertIdx++, store);
        } else {
            // --- 数组处理 (展开复制) ---
            // 【关键修复】先替换现有引用，再创建初始化代码
            // 否则初始化 GEP 的 base (@a) 也会被错误替换成 localAlloca，导致初始值丢失！
            replaceUsers(func, gv, localAlloca);
            
            for (int i = 0; i < arraySize; i++) {
                ConstantInt idx = new ConstantInt(i);

                // 从全局数组读取初始值
                GetElementPtrInst gepG = new GetElementPtrInst(gv, Arrays.asList(new ConstantInt(0), idx), "gep_init_g_" + i, null);
                gepG.setParentBlock(entry);
                entry.getInstructions().add(insertIdx++, gepG);

                LoadInst load = new LoadInst(gepG, "val_init_" + i, null);
                load.setParentBlock(entry);
                entry.getInstructions().add(insertIdx++, load);

                // 存储到局部数组
                GetElementPtrInst gepL = new GetElementPtrInst(localAlloca, Arrays.asList(new ConstantInt(0), idx), "gep_init_l_" + i, null);
                gepL.setParentBlock(entry);
                entry.getInstructions().add(insertIdx++, gepL);
                
                StoreInst store = new StoreInst(load, gepL, null);
                store.setParentBlock(entry);
                entry.getInstructions().add(insertIdx++, store);
            }
        }

        // 3. 写回
        // 【激进优化】main 函数结束后程序就退出了，可以跳过写回
        if (Config.AGGRESSIVE_MODE && func.getName().equals("@main")) {
            return;
        }
        
        // 基本模式：必须正确写回
        if (isDirty) {
            for (BasicBlock bb : func.getBasicBlocks()) {
                if (bb.getInstructions().isEmpty()) continue;
                Instruction term = bb.getInstructions().get(bb.getInstructions().size() - 1);
                
                if (term instanceof ReturnInst) {
                    int retIdx = bb.getInstructions().indexOf(term);
                    
                    if (!isArray) {
                        LoadInst finalLoad = new LoadInst(localAlloca, "final_" + gv.getName(), null);
                        finalLoad.setParentBlock(bb);
                        
                        StoreInst finalStore = new StoreInst(finalLoad, gv, null);
                        finalStore.setParentBlock(bb);
                        
                        bb.getInstructions().add(retIdx, finalLoad);
                        bb.getInstructions().add(retIdx + 1, finalStore);
                    } else {
                        // 数组写回
                        for (int i = 0; i < arraySize; i++) {
                            ConstantInt idx = new ConstantInt(i);
                             
                            GetElementPtrInst gepL = new GetElementPtrInst(localAlloca, Arrays.asList(new ConstantInt(0), idx), "gep_wb_l_" + i, null);
                            gepL.setParentBlock(bb);
                            bb.getInstructions().add(retIdx++, gepL);
                             
                            LoadInst load = new LoadInst(gepL, "wb_val_" + i, null);
                            load.setParentBlock(bb);
                            bb.getInstructions().add(retIdx++, load);
                             
                            GetElementPtrInst gepG = new GetElementPtrInst(gv, Arrays.asList(new ConstantInt(0), idx), "gep_wb_g_" + i, null);
                            gepG.setParentBlock(bb);
                            bb.getInstructions().add(retIdx++, gepG);
                             
                            StoreInst store = new StoreInst(load, gepG, null);
                            store.setParentBlock(bb);
                            bb.getInstructions().add(retIdx++, store);
                        }
                    }
                }
            }
        }
    }
    
    private void replaceUsers(Function func, GlobalVariable gv, AllocaInst localAlloca) {
        List<Use> users = new ArrayList<>(gv.getUsers());
        for (Use use : users) {
            User user = use.getUser();
            if (user instanceof Instruction inst && inst.getParentBlock().getParentFunction() == func) {
                if (inst instanceof LoadInst load && load.getPointer() == gv) {
                    load.setOperand(0, localAlloca);
                } else if (inst instanceof StoreInst store && store.getPointer() == gv) {
                    store.setOperand(1, localAlloca);
                } else if (inst instanceof GetElementPtrInst gep && gep.getOperand(0) == gv) {
                    gep.setOperand(0, localAlloca);
                }
            }
        }
    }
}
