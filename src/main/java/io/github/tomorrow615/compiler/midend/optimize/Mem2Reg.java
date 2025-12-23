package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.analysis.DominatorTree;
import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.type.IntegerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.*;

/**
 * Mem2Reg 优化 Pass
 * 将可提升的 alloca 指令转换为 SSA 形式（使用 Phi 节点）
 *
 * 算法：
 * 1. 筛选可提升的 alloca（基本类型，未逃逸）
 * 2. 使用支配边界 (DF) 插入 Phi 节点
 * 3. 使用支配树进行变量重命名
 */
public class Mem2Reg implements Pass {

    @Override
    public String getName() {
        return "Mem2Reg";
    }


    @Override
    public void runOnModule(Module module) {
        for (Function func : module.getFunctions()) {
            if (!func.isDeclaration()) {
                runOnFunction(func);
            }
        }
    }

    @Override
    public void runOnFunction(Function func) {
        // 构建支配树
        DominatorTree domTree = new DominatorTree(func);

        // 1. 收集所有可提升的 alloca
        List<AllocaInst> promotableAllocas = collectPromotableAllocas(func);
        if (promotableAllocas.isEmpty()) {
            return;
        }

        // 2. 为每个 alloca 收集定义块（有 store 的块）和使用块（有 load 的块）
        Map<AllocaInst, Set<BasicBlock>> defBlocks = new HashMap<>();
        Map<AllocaInst, Set<BasicBlock>> useBlocks = new HashMap<>();
        for (AllocaInst alloca : promotableAllocas) {
            defBlocks.put(alloca, new HashSet<>());
            useBlocks.put(alloca, new HashSet<>());
        }
        collectDefUseBlocks(func, promotableAllocas, defBlocks, useBlocks);

        // 3. 插入 Phi 节点
        Map<AllocaInst, Map<BasicBlock, PhiInst>> insertedPhis = insertPhiNodes(domTree, promotableAllocas, defBlocks);

        // 4. 变量重命名
        rename(domTree, promotableAllocas, insertedPhis);

        // 5. 清理：删除已提升的 alloca, load, store
        cleanup(func, promotableAllocas);

        // 6. 清理冗余的 Phi 节点
        removeTrivialPhis(func);
    }

    // ========== Step 1: 收集可提升的 alloca ==========

    private List<AllocaInst> collectPromotableAllocas(Function func) {
        List<AllocaInst> result = new ArrayList<>();

        // 只处理 entry block 中的 alloca（标准 LLVM 约定）
        BasicBlock entryBlock = func.getBasicBlocks().get(0);
        for (Instruction inst : entryBlock.getInstructions()) {
            if (inst instanceof AllocaInst alloca) {
                if (isPromotable(alloca)) {
                    result.add(alloca);
                }
            }
        }
        return result;
    }

    /**
     * 判断 alloca 是否可提升
     * 条件：
     * 1. 分配的是基本类型（i32）
     * 2. 只被 load/store 使用，且 store 的目标和 load 的源都是该 alloca
     * 3. 没有地址逃逸（未作为函数参数传递，未被 GEP 等取址）
     */
    private boolean isPromotable(AllocaInst alloca) {
        Type allocatedType = alloca.getAllocatedType();

        // 只处理 i32 类型
        if (!(allocatedType instanceof IntegerType intType) || intType.getBitWidth() != 32) {
            return false;
        }

        // 检查所有使用者
        for (Use use : alloca.getUsers()) {
            User user = use.getUser();
            if (user instanceof LoadInst load) {
                // load 的指针必须是该 alloca
                if (load.getPointer() != alloca) {
                    return false;
                }
            } else if (user instanceof StoreInst store) {
                // store 的目标指针必须是该 alloca，且不能把 alloca 的地址存到别的地方
                if (store.getPointer() != alloca) {
                    return false; // alloca 被当作值存储到别处 -> 地址逃逸
                }
            } else {
                // 其他使用（GEP, Call 等）-> 不可提升
                return false;
            }
        }
        return true;
    }

    // ========== Step 2: 收集定义/使用块 ==========

    private void collectDefUseBlocks(Function func,
                                     List<AllocaInst> promotableAllocas,
                                     Map<AllocaInst, Set<BasicBlock>> defBlocks,
                                     Map<AllocaInst, Set<BasicBlock>> useBlocks) {
        Set<AllocaInst> allocaSet = new HashSet<>(promotableAllocas);

        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof StoreInst store) {
                    Value ptr = store.getPointer();
                    if (ptr instanceof AllocaInst alloca && allocaSet.contains(alloca)) {
                        defBlocks.get(alloca).add(bb);
                    }
                } else if (inst instanceof LoadInst load) {
                    Value ptr = load.getPointer();
                    if (ptr instanceof AllocaInst alloca && allocaSet.contains(alloca)) {
                        useBlocks.get(alloca).add(bb);
                    }
                }
            }
        }
    }

    // ========== Step 3: 插入 Phi 节点 ==========

    private Map<AllocaInst, Map<BasicBlock, PhiInst>> insertPhiNodes(
            DominatorTree domTree,
            List<AllocaInst> promotableAllocas,
            Map<AllocaInst, Set<BasicBlock>> defBlocks) {

        Map<AllocaInst, Map<BasicBlock, PhiInst>> insertedPhis = new HashMap<>();

        for (AllocaInst alloca : promotableAllocas) {
            insertedPhis.put(alloca, new HashMap<>());

            // 迭代支配边界算法
            Set<BasicBlock> phiBlocks = new HashSet<>(); // 已插入 Phi 的块
            Queue<BasicBlock> worklist = new LinkedList<>(defBlocks.get(alloca));
            Set<BasicBlock> visited = new HashSet<>(defBlocks.get(alloca));

            while (!worklist.isEmpty()) {
                BasicBlock defBlock = worklist.poll();

                for (BasicBlock dfBlock : domTree.getDominanceFrontier(defBlock)) {
                    if (!phiBlocks.contains(dfBlock)) {
                        // 在 dfBlock 开头插入 Phi
                        PhiInst phi = createPhiForAlloca(alloca, dfBlock);
                        insertedPhis.get(alloca).put(dfBlock, phi);
                        phiBlocks.add(dfBlock);

                        // 如果这个块不在已访问集合中，加入 worklist
                        if (!visited.contains(dfBlock)) {
                            visited.add(dfBlock);
                            worklist.add(dfBlock);
                        }
                    }
                }
            }
        }
        return insertedPhis;
    }

    private PhiInst createPhiForAlloca(AllocaInst alloca, BasicBlock block) {
        Type type = alloca.getAllocatedType();
        // 创建 Phi，但不自动加入 block 的指令列表
        // 因为我们需要手动插入到块的开头
        PhiInst phi = new PhiInst(type, "phi." + alloca.getName(), null);
        phi.setParentBlock(block);

        // 将 Phi 插入到块的开头（在所有其他 Phi 之后，其他指令之前）
        List<Instruction> instructions = block.getInstructions();
        int insertIndex = 0;
        for (int i = 0; i < instructions.size(); i++) {
            if (instructions.get(i) instanceof PhiInst) {
                insertIndex = i + 1;
            } else {
                break;
            }
        }
        instructions.add(insertIndex, phi);
        return phi;
    }

    // ========== Step 4: 变量重命名 ==========

    private void rename(DominatorTree domTree,
                        List<AllocaInst> promotableAllocas,
                        Map<AllocaInst, Map<BasicBlock, PhiInst>> insertedPhis) {

        // 为每个 alloca 维护一个值栈
        Map<AllocaInst, Deque<Value>> valueStacks = new HashMap<>();
        for (AllocaInst alloca : promotableAllocas) {
            Deque<Value> stack = new ArrayDeque<>();
            // 初始值为 undef（用 ConstantInt 0 代替，保守处理）
            stack.push(new ConstantInt(0));
            valueStacks.put(alloca, stack);
        }

        Set<AllocaInst> allocaSet = new HashSet<>(promotableAllocas);

        // DFS 遍历支配树
        renameBlock(domTree.getEntryBlock(), domTree, allocaSet, valueStacks, insertedPhis);
    }

    private void renameBlock(BasicBlock block,
                             DominatorTree domTree,
                             Set<AllocaInst> allocaSet,
                             Map<AllocaInst, Deque<Value>> valueStacks,
                             Map<AllocaInst, Map<BasicBlock, PhiInst>> insertedPhis) {

        // 记录本层 push 了多少次，用于退出时 pop
        Map<AllocaInst, Integer> pushCounts = new HashMap<>();
        for (AllocaInst alloca : allocaSet) {
            pushCounts.put(alloca, 0);
        }

        // 1. 处理本块中的 Phi 定义
        for (AllocaInst alloca : allocaSet) {
            PhiInst phi = insertedPhis.get(alloca).get(block);
            if (phi != null) {
                // Phi 本身就是新的定义
                valueStacks.get(alloca).push(phi);
                pushCounts.put(alloca, pushCounts.get(alloca) + 1);
            }
        }

        // 2. 处理本块中的指令
        List<Instruction> instructions = block.getInstructions();
        for (Instruction inst : instructions) {
            if (inst instanceof LoadInst load) {
                Value ptr = load.getPointer();
                if (ptr instanceof AllocaInst alloca && allocaSet.contains(alloca)) {
                    // 用栈顶值替换 load 的所有使用
                    Value currentValue = valueStacks.get(alloca).peek();
                    replaceAllUsesWith(load, currentValue);
                }
            } else if (inst instanceof StoreInst store) {
                Value ptr = store.getPointer();
                if (ptr instanceof AllocaInst alloca && allocaSet.contains(alloca)) {
                    // 把存入的值压入栈
                    Value storedValue = store.getValue();
                    valueStacks.get(alloca).push(storedValue);
                    pushCounts.put(alloca, pushCounts.get(alloca) + 1);
                }
            }
        }

        // 3. 填充后继块的 Phi
        for (BasicBlock succ : block.getSuccessors()) {
            for (AllocaInst alloca : allocaSet) {
                PhiInst phi = insertedPhis.get(alloca).get(succ);
                if (phi != null) {
                    Value currentValue = valueStacks.get(alloca).peek();
                    phi.addIncoming(currentValue, block);
                }
            }
        }

        // 4. 递归处理支配树子节点
        for (BasicBlock child : domTree.getDomTreeChildren(block)) {
            renameBlock(child, domTree, allocaSet, valueStacks, insertedPhis);
        }

        // 5. 恢复现场：弹出本层压入的值
        for (AllocaInst alloca : allocaSet) {
            int count = pushCounts.get(alloca);
            for (int i = 0; i < count; i++) {
                valueStacks.get(alloca).pop();
            }
        }
    }

    /**
     * 将 oldValue 的所有使用替换为 newValue
     */
    private void replaceAllUsesWith(Value oldValue, Value newValue) {
        // 注意：需要复制列表，因为迭代过程中会修改
        List<Use> uses = new ArrayList<>(oldValue.getUsers());
        for (Use use : uses) {
            use.setValue(newValue);
        }
    }

    // ========== Step 5: 清理 ==========

    private void cleanup(Function func, List<AllocaInst> promotableAllocas) {
        Set<AllocaInst> allocaSet = new HashSet<>(promotableAllocas);
        Set<Instruction> toRemove = new HashSet<>();

        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof AllocaInst alloca && allocaSet.contains(alloca)) {
                    toRemove.add(inst);
                } else if (inst instanceof LoadInst load) {
                    Value ptr = load.getPointer();
                    if (ptr instanceof AllocaInst alloca && allocaSet.contains(alloca)) {
                        toRemove.add(inst);
                    }
                } else if (inst instanceof StoreInst store) {
                    Value ptr = store.getPointer();
                    if (ptr instanceof AllocaInst alloca && allocaSet.contains(alloca)) {
                        toRemove.add(inst);
                    }
                }
            }
        }

        // 从各块中移除
        for (BasicBlock bb : func.getBasicBlocks()) {
            bb.getInstructions().removeAll(toRemove);
        }
    }

    // ========== Step 6: 清理冗余 Phi 节点 ==========

    /**
     * 移除冗余的 Phi 节点
     * 冗余的情况包括：
     * 1. phi [x, BB1], [x, BB2] ... - 所有输入都是同一个值
     * 2. phi [x, BB1] - 只有一个输入
     * 3. phi [phi, BB1], [x, BB2] - 包含自引用，但实际值只有一个
     */
    private void removeTrivialPhis(Function func) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (BasicBlock bb : func.getBasicBlocks()) {
                List<Instruction> toRemove = new ArrayList<>();
                for (Instruction inst : bb.getInstructions()) {
                    if (inst instanceof PhiInst phi) {
                        Value common = null;
                        boolean isTrivial = true;
                        
                        // 检查所有输入值
                        for (int i = 0; i < phi.getOperands().size(); i += 2) {
                            Value val = phi.getOperand(i);
                            // 跳过自引用的情况 (phi = phi)
                            if (val == phi) continue;
                            if (common == null) {
                                common = val;
                            } else if (common != val) {
                                isTrivial = false;
                                break;
                            }
                        }
                        
                        // 如果所有输入都相同（或者只有自引用），则替换
                        if (isTrivial && common != null) {
                            replaceAllUsesWith(phi, common);
                            toRemove.add(phi);
                            changed = true;
                        }
                    } else {
                        break; // Phi 都在块开头
                    }
                }
                bb.getInstructions().removeAll(toRemove);
            }
        }
    }
}
