package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.analysis.DominatorTree;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.type.IntegerType;
import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.*;

/**
 * Mem2Reg 优化 Pass (SSA 构造)
 * 
 * 将只被 load/store 使用的 alloca 提升为虚拟寄存器，
 * 通过插入 Phi 节点处理控制流汇聚点。
 * 
 * 可提升的 alloca 条件:
 * 1. 只被 load 和 store 指令使用
 * 2. 类型是标量（i32, i1）而非数组或聚合类型
 * 
 * 算法步骤:
 * 1. 识别可提升的 alloca
 * 2. 在支配边界插入 Phi 节点
 * 3. 重命名变量（DFS 遍历支配树）
 * 4. 删除原有的 alloca/load/store
 */
public class Mem2Reg implements Pass {

    private DominatorTree domTree;
    private Map<AllocaInst, Set<BasicBlock>> defBlocks; // alloca 被 store 的块
    private Map<AllocaInst, Type> allocaTypes;           // alloca 的元素类型
    
    @Override
    public String getName() {
        return "Mem2Reg";
    }

    @Override
    public void runOnFunction(Function function) {
        // 1. 先构建支配树
        domTree = new DominatorTree();
        domTree.runOnFunction(function);
        
        // 2. 找到可提升的 alloca
        List<AllocaInst> promotableAllocas = findPromotableAllocas(function);
        if (promotableAllocas.isEmpty()) {
            return;
        }
        
        // 3. 收集每个 alloca 被 store 的基本块
        defBlocks = new HashMap<>();
        allocaTypes = new HashMap<>();
        for (AllocaInst alloca : promotableAllocas) {
            defBlocks.put(alloca, new HashSet<>());
            allocaTypes.put(alloca, alloca.getAllocatedType());
            
            for (Use use : alloca.getUsers()) {
                if (use.getUser() instanceof StoreInst store) {
                    defBlocks.get(alloca).add(store.getParentBlock());
                }
            }
        }
        
        // 4. 插入 Phi 节点
        Map<AllocaInst, Map<BasicBlock, PhiInst>> insertedPhis = insertPhiNodes(function, promotableAllocas);
        
        // 5. 重命名变量
        rename(function, promotableAllocas, insertedPhis);
        
        // 6. 删除原有的 alloca/load/store
        cleanup(function, promotableAllocas);
    }

    /**
     * 找到可以提升的 alloca 指令
     */
    private List<AllocaInst> findPromotableAllocas(Function function) {
        List<AllocaInst> result = new ArrayList<>();
        
        for (BasicBlock bb : function.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof AllocaInst alloca) {
                    if (isPromotable(alloca)) {
                        result.add(alloca);
                    }
                }
            }
        }
        
        return result;
    }

    /**
     * 判断 alloca 是否可以提升
     */
    private boolean isPromotable(AllocaInst alloca) {
        Type allocatedType = alloca.getAllocatedType();
        
        // 只提升标量类型（i32, i1）
        if (!(allocatedType instanceof IntegerType)) {
            return false;
        }
        
        // 检查所有使用者：只能是 load 或 store
        for (Use use : alloca.getUsers()) {
            User user = use.getUser();
            if (user instanceof LoadInst load) {
                // load 的指针必须是这个 alloca
                if (load.getPointer() != alloca) {
                    return false;
                }
            } else if (user instanceof StoreInst store) {
                // store 的目标指针必须是这个 alloca（不是存的值）
                if (store.getPointer() != alloca) {
                    return false;
                }
            } else {
                // 有其他类型的使用（如 GEP），不能提升
                return false;
            }
        }
        
        return true;
    }

    /**
     * 在支配边界插入 Phi 节点
     */
    private Map<AllocaInst, Map<BasicBlock, PhiInst>> insertPhiNodes(
            Function function, List<AllocaInst> allocas) {
        
        Map<AllocaInst, Map<BasicBlock, PhiInst>> result = new HashMap<>();
        
        for (AllocaInst alloca : allocas) {
            result.put(alloca, new HashMap<>());
            
            Set<BasicBlock> defBlocksSet = defBlocks.get(alloca);
            Set<BasicBlock> processed = new HashSet<>();
            Queue<BasicBlock> worklist = new LinkedList<>(defBlocksSet);
            
            while (!worklist.isEmpty()) {
                BasicBlock bb = worklist.poll();
                
                for (BasicBlock df : domTree.getDomFrontier(bb)) {
                    if (!result.get(alloca).containsKey(df)) {
                        // 在 df 开头插入 Phi
                        PhiInst phi = new PhiInst(
                            allocaTypes.get(alloca),
                            alloca.getName() + ".phi",
                            null  // 先不加入基本块
                        );
                        result.get(alloca).put(df, phi);
                        
                        // 将 Phi 插入到基本块开头
                        df.getInstructions().add(0, phi);
                        phi.setParentBlock(df);
                        
                        // df 现在也是定义点
                        if (!processed.contains(df)) {
                            processed.add(df);
                            worklist.add(df);
                        }
                    }
                }
            }
        }
        
        return result;
    }

    /**
     * 重命名变量：DFS 遍历支配树
     */
    private void rename(Function function, List<AllocaInst> allocas,
                        Map<AllocaInst, Map<BasicBlock, PhiInst>> insertedPhis) {
        
        // 每个 alloca 对应一个值栈
        Map<AllocaInst, Deque<Value>> stacks = new HashMap<>();
        for (AllocaInst alloca : allocas) {
            stacks.put(alloca, new ArrayDeque<>());
            // 初始值为 undef（用 0 代替）
            stacks.get(alloca).push(new ConstantInt(0));
        }
        
        // 从入口块开始 DFS
        if (!function.getBasicBlocks().isEmpty()) {
            BasicBlock entry = function.getBasicBlocks().get(0);
            renameBlock(entry, allocas, insertedPhis, stacks, new HashSet<>());
        }
    }

    private void renameBlock(BasicBlock bb, List<AllocaInst> allocas,
                             Map<AllocaInst, Map<BasicBlock, PhiInst>> insertedPhis,
                             Map<AllocaInst, Deque<Value>> stacks,
                             Set<BasicBlock> visited) {
        if (visited.contains(bb)) return;
        visited.add(bb);
        
        // 记录每个 alloca 在这个块里 push 了多少次，以便退出时 pop
        Map<AllocaInst, Integer> pushCounts = new HashMap<>();
        for (AllocaInst alloca : allocas) {
            pushCounts.put(alloca, 0);
        }
        
        // 处理这个块新插入的 Phi 节点（它们定义新值）
        for (AllocaInst alloca : allocas) {
            if (insertedPhis.get(alloca).containsKey(bb)) {
                PhiInst phi = insertedPhis.get(alloca).get(bb);
                stacks.get(alloca).push(phi);
                pushCounts.put(alloca, pushCounts.get(alloca) + 1);
            }
        }
        
        // 处理块内指令
        List<Instruction> instructions = bb.getInstructions();
        List<Instruction> toRemove = new ArrayList<>();
        
        for (Instruction inst : instructions) {
            if (inst instanceof LoadInst load) {
                // 检查是否是对可提升 alloca 的 load
                for (AllocaInst alloca : allocas) {
                    if (load.getPointer() == alloca) {
                        // 用栈顶值替换这个 load 的所有使用
                        Value stackTop = stacks.get(alloca).peek();
                        replaceAllUsesWith(load, stackTop);
                        toRemove.add(load);
                        break;
                    }
                }
            } else if (inst instanceof StoreInst store) {
                // 检查是否是对可提升 alloca 的 store
                for (AllocaInst alloca : allocas) {
                    if (store.getPointer() == alloca) {
                        // 将存的值 push 到栈上
                        stacks.get(alloca).push(store.getValue());
                        pushCounts.put(alloca, pushCounts.get(alloca) + 1);
                        toRemove.add(store);
                        break;
                    }
                }
            }
        }
        
        // 删除这个块里需要删除的指令
        instructions.removeAll(toRemove);
        
        // 填充后继块的 Phi 节点
        for (BasicBlock succ : bb.getSuccessors()) {
            for (AllocaInst alloca : allocas) {
                if (insertedPhis.get(alloca).containsKey(succ)) {
                    PhiInst phi = insertedPhis.get(alloca).get(succ);
                    Value stackTop = stacks.get(alloca).peek();
                    phi.addIncoming(stackTop, bb);
                }
            }
        }
        
        // DFS 遍历支配树子节点
        for (BasicBlock child : domTree.getChildren(bb)) {
            renameBlock(child, allocas, insertedPhis, stacks, visited);
        }
        
        // 退出时 pop
        for (AllocaInst alloca : allocas) {
            int count = pushCounts.get(alloca);
            for (int i = 0; i < count; i++) {
                stacks.get(alloca).pop();
            }
        }
    }

    /**
     * 清理：删除原有的 alloca 指令
     */
    private void cleanup(Function function, List<AllocaInst> allocas) {
        Set<AllocaInst> allocaSet = new HashSet<>(allocas);
        
        for (BasicBlock bb : function.getBasicBlocks()) {
            bb.getInstructions().removeIf(inst -> allocaSet.contains(inst));
        }
    }

    private void replaceAllUsesWith(Value oldValue, Value newValue) {
        List<Use> usersSnapshot = new ArrayList<>(oldValue.getUsers());
        for (Use use : usersSnapshot) {
            use.setValue(newValue);
        }
    }
}
