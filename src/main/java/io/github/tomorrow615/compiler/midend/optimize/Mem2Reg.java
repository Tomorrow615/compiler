package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.*;

/**
 * Mem2Reg Pass - Memory to Register 提升
 * 将局部变量从栈内存模式提升为 SSA 寄存器模式
 * 消除 alloca, load, store 指令
 */
public class Mem2Reg implements Pass {
    // 支配树分析（前置依赖）
    private DomAnalysis domAnalysis;
    
    // 记录每个 Alloca 的定义点集合（被 store 的基本块）
    private Map<AllocaInst, Set<BasicBlock>> defBlocks;
    
    // 变量重命名时的值栈
    private Map<AllocaInst, Stack<Value>> varStacks;
    
    // 已插入 Phi 的块记录
    private Map<AllocaPair, PhiInst> insertedPhis;
    
    // Phi 到 Alloca 的反向映射（优化查找性能）
    private Map<PhiInst, AllocaInst> phiToAllocaMap;
    
    // 待删除的指令列表
    private Set<Instruction> toDelete;
    
    @Override
    public String getName() {
        return "Mem2Reg";
    }

    @Override
    public void runOnFunction(Function func) {
        if (func.isDeclaration()) {
            return;
        }
        
        // 0. 初始化
        domAnalysis = new DomAnalysis();
        domAnalysis.runOnFunction(func);
        
        defBlocks = new HashMap<>();
        varStacks = new HashMap<>();
        insertedPhis = new HashMap<>();
        phiToAllocaMap = new HashMap<>();
        toDelete = new HashSet<>();
        
        // 1. 收集可提升的 alloca，初始化变量栈
        List<AllocaInst> allocas = collectPromotableAllocas(func);
        if (allocas.isEmpty()) {
            return;  // 没有可提升的变量，提前退出
        }
        
        for (AllocaInst alloca : allocas) {
            varStacks.put(alloca, new Stack<>());
            // 初始化：未定义变量设为 0（而非 undef）
            // 原因：1) SysY 语义下未初始化行为是未定义的，用 0 是合理默认
            //       2) 避免后端需要处理 UndefValue 的复杂性
            Type targetType = ((PointerType)alloca.getType()).getTargetType();
            if (targetType instanceof IntegerType) {
                IntegerType intType = (IntegerType) targetType;
                varStacks.get(alloca).push(new ConstantInt(0, intType));
            } else {
                // 未来扩展：如果支持浮点数，这里应该 push ConstantFloat(0.0)
                varStacks.get(alloca).push(new ConstantInt(0));
            }
        }
        
        // 2. 插入 Phi 节点
        insertPhis(func, allocas);
        
        // 3. 变量重命名
        BasicBlock entry = func.getBasicBlocks().get(0);
        rename(entry);
        
        // 4. 删除 alloca/load/store
        for (AllocaInst alloca : allocas) {
            toDelete.add(alloca);
        }
        cleanup();
    }

    // ==================== 辅助函数 ====================
    
    /**
     * 收集可提升的 alloca（基本类型且未逃逸）
     */
    private List<AllocaInst> collectPromotableAllocas(Function func) {
        List<AllocaInst> result = new ArrayList<>();
        BasicBlock entry = func.getBasicBlocks().get(0);
        
        for (Instruction inst : entry.getInstructions()) {
            if (inst instanceof AllocaInst) {
                AllocaInst alloca = (AllocaInst) inst;
                Type targetType = ((PointerType)alloca.getType()).getTargetType();
                
                // 只提升基本类型（目前仅支持整数类型）且未逃逸的变量
                // 注意：如果将来需要支持 float，需要添加：|| targetType.isFloatType()
                if (targetType.isIntegerType() && isAllocaPromotable(alloca)) {
                    result.add(alloca);
                }
            }
        }
        
        return result;
    }
    
    /**
     * 判断 alloca 是否可提升（检查地址是否逃逸）
     * 只有被 Load/Store 使用的 alloca 才能安全提升
     */
    private boolean isAllocaPromotable(AllocaInst alloca) {
        for (Use use : alloca.getUsers()) {
            User user = use.getUser();
            
            // Load 操作：安全（直接读取）
            if (user instanceof LoadInst) {
                continue;
            }
            
            // Store 操作：必须是作为目标地址，而不是被存储的值
            if (user instanceof StoreInst) {
                StoreInst store = (StoreInst) user;
                if (store.getPointer() == alloca) {
                    continue;  // 安全：作为 store 的目标
                }
                // 如果 alloca 被当作值存储，说明地址逃逸了
                return false;
            }
            
            // 其他任何使用（Call, GEP, BitCast等）都认为不可提升
            return false;
        }
        return true;
    }
    
    /**
     * 收集定义点（被 store 的基本块）
     */
    private Set<BasicBlock> collectDefBlocks(AllocaInst alloca) {
        Set<BasicBlock> defs = new HashSet<>();
        
        for (Use use : alloca.getUsers()) {
            Value user = use.getUser();
            if (user instanceof StoreInst) {
                StoreInst store = (StoreInst) user;
                if (store.getPointer() == alloca) {
                    defs.add(store.getParentBlock());
                }
            }
        }
        
        return defs;
    }
    
    /**
     * 反查 Phi 对应的 alloca（O(1) 查找）
     */
    private AllocaInst findAllocaForPhi(PhiInst phi) {
        return phiToAllocaMap.get(phi);
    }

    // ==================== Phi 插入 ====================
    
    /**
     * 在支配边界插入 Phi 节点
     */
    private void insertPhis(Function func, List<AllocaInst> allocas) {
        for (AllocaInst alloca : allocas) {
            // 收集定义点
            Set<BasicBlock> defs = collectDefBlocks(alloca);
            defBlocks.put(alloca, defs);
            
            // 工作集：所有定义点
            Queue<BasicBlock> workList = new LinkedList<>(defs);
            Set<BasicBlock> processed = new HashSet<>();
            
            while (!workList.isEmpty()) {
                BasicBlock x = workList.poll();
                
                // 遍历 X 的支配边界
                for (BasicBlock y : domAnalysis.getDominanceFrontier(x)) {
                    // 如果 Y 还没有插入过该 alloca 的 Phi
                    AllocaPair key = new AllocaPair(alloca, y);
                    if (!insertedPhis.containsKey(key)) {
                        // 插入 Phi 节点
                    Type varType = ((PointerType)alloca.getType()).getTargetType();
                    String phiName = alloca.getName().isEmpty() ? "phi" : alloca.getName() + ".phi";
                    PhiInst phi = new PhiInst(varType, phiName, y);
                    
                    // 添加到基本块开头（在所有非 Phi 指令之前）
                    List<Instruction> insts = y.getInstructions();
                    int insertPos = 0;
                    while (insertPos < insts.size() && insts.get(insertPos) instanceof PhiInst) {
                        insertPos++;
                    }
                    insts.add(insertPos, phi);
                    
                    insertedPhis.put(key, phi);
                    phiToAllocaMap.put(phi, alloca);  // 记录反向映射
                        
                        // Phi 也是一种定义，加入工作集
                        if (!processed.contains(y)) {
                            workList.add(y);
                            processed.add(y);
                        }
                    }
                }
            }
        }
    }

    // ==================== 变量重命名 ====================
    
    /**
     * 变量重命名（DFS 支配树）
     */
    private void rename(BasicBlock bb) {
        // 1. 记录栈的初始高度（用于恢复现场）
        Map<AllocaInst, Integer> stackHeights = new HashMap<>();
        for (AllocaInst alloca : varStacks.keySet()) {
            stackHeights.put(alloca, varStacks.get(alloca).size());
        }
        
        // 2. 处理该块中的 Phi 指令
        for (Instruction inst : bb.getInstructions()) {
            if (!(inst instanceof PhiInst)) break;  // Phi 都在开头
            
            PhiInst phi = (PhiInst) inst;
            // 找到对应的 alloca
            AllocaInst alloca = findAllocaForPhi(phi);
            if (alloca != null && varStacks.containsKey(alloca)) {
                varStacks.get(alloca).push(phi);
            }
        }
        
        // 3. 处理普通指令
        List<Instruction> instructions = new ArrayList<>(bb.getInstructions());
        for (Instruction inst : instructions) {
            if (inst instanceof LoadInst) {
                LoadInst load = (LoadInst) inst;
                Value ptr = load.getPointer();
                
                if (ptr instanceof AllocaInst && varStacks.containsKey(ptr)) {
                    AllocaInst alloca = (AllocaInst) ptr;
                    // 用栈顶的值替换 load
                    Value newValue = varStacks.get(alloca).peek();
                    load.replaceAllUsesWith(newValue);
                    toDelete.add(load);
                }
            } else if (inst instanceof StoreInst) {
                StoreInst store = (StoreInst) inst;
                Value ptr = store.getPointer();
                
                if (ptr instanceof AllocaInst && varStacks.containsKey(ptr)) {
                    AllocaInst alloca = (AllocaInst) ptr;
                    // 更新变量栈
                    varStacks.get(alloca).push(store.getValue());
                    toDelete.add(store);
                }
            }
        }
        
        // 4. 填充后继块的 Phi 参数
        for (BasicBlock succ : bb.getSuccessors()) {
            for (Instruction inst : succ.getInstructions()) {
                if (!(inst instanceof PhiInst)) break;
                
                PhiInst phi = (PhiInst) inst;
                AllocaInst alloca = findAllocaForPhi(phi);
                if (alloca != null && varStacks.containsKey(alloca)) {
                    Value value = varStacks.get(alloca).peek();
                    phi.addIncoming(value, bb);
                }
            }
        }
        
        // 5. 递归处理支配树的子节点
        for (BasicBlock child : domAnalysis.getDomTreeChildren(bb)) {
            rename(child);
        }
        
        // 6. 恢复现场（弹出栈）
        for (AllocaInst alloca : varStacks.keySet()) {
            Stack<Value> stack = varStacks.get(alloca);
            int targetHeight = stackHeights.get(alloca);
            while (stack.size() > targetHeight) {
                stack.pop();
            }
        }
    }

    // ==================== 清理 ====================
    
    /**
     * 删除标记的指令
     */
    private void cleanup() {
        for (Instruction inst : toDelete) {
            BasicBlock bb = inst.getParentBlock();
            if (bb != null) {
                bb.getInstructions().remove(inst);
            }
        }
    }

    // ==================== 辅助类 ====================
    
    /**
     * (Alloca, BasicBlock) 键值对
     */
    private static class AllocaPair {
        final AllocaInst alloca;
        final BasicBlock block;
        
        AllocaPair(AllocaInst alloca, BasicBlock block) {
            this.alloca = alloca;
            this.block = block;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AllocaPair)) return false;
            AllocaPair that = (AllocaPair) o;
            return Objects.equals(alloca, that.alloca) && Objects.equals(block, that.block);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(alloca, block);
        }
    }
}
