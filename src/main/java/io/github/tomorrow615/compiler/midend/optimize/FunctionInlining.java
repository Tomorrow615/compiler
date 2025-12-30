package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * [Phase 1.5] 函数内联 Pass
 * 
 * 策略：
 * 1. 扫描所有 Call 指令
 * 2. 如果被调函数符合内联条件（非递归，指令少），则进行内联
 * 3. 内联涉及：基本块分裂、代码克隆、参数映射、返回值聚合
 */
public class FunctionInlining implements Pass {

    // 激进的内联阈值：允许内联较大的函数
    private static final int INLINE_THRESHOLD = 100;
    
    // Caller 体积保护：防止单个函数过大导致寄存器分配崩溃
    private static final int MAX_CALLER_SIZE = 10000;
    
    // 内联计数器（静态，确保多轮迭代时唯一性）
    private static int inlineCounter = 0;

    @Override
    public String getName() {
        return "FunctionInlining";
    }

    @Override
    public void runOnModule(Module module) {
        // 【激进内联】单轮执行：每次调用只展开一层
        // 递归展开由外层 Compiler.java 的多轮迭代控制
        // 这样避免了内部 while 循环导致的死循环
        
        // 收集所有需要内联的调用点 (CallInst)
        List<CallInst> inlineCandidates = new ArrayList<>();
        
        for (Function func : module.getFunctions()) {
            if (func.isDeclaration()) continue;
            for (BasicBlock bb : func.getBasicBlocks()) {
                for (Instruction inst : bb.getInstructions()) {
                    if (inst instanceof CallInst call) {
                        Function callee = call.getFunction();
                        if (canInline(callee, func)) {
                            inlineCandidates.add(call);
                        }
                    }
                }
            }
        }

        // 【修复】限制每轮内联的数量，防止单轮内联过多导致代码膨胀
        int MAX_INLINES_PER_ROUND = 50;
        int inlineCount = 0;
        
        // 执行内联（只展开一层）
        for (CallInst call : inlineCandidates) {
            // 【安全检查】确保 callInst 仍然有效（父块还存在）
            if (call.getParentBlock() == null) continue;
            Function caller = call.getParentBlock().getParentFunction();
            if (caller == null) continue;
            
            // 【安全检查】再次检查 caller 大小，防止前面的内联导致 caller 过大
            if (countInstructions(caller) > MAX_CALLER_SIZE) continue;
            
            performInline(call);
            inlineCount++;
            
            // 达到限制则停止本轮内联
            if (inlineCount >= MAX_INLINES_PER_ROUND) break;
        }
    }

    @Override
    public void runOnFunction(Function function) {
        // 内联需要 Module 级别的信息，使用 runOnModule 代替
        // 此方法为空实现，不会被调用
    }


    /**
     * 判断是否可以内联
     * 
     * 激进策略（配合洋葱剥皮法）：
     * 1. 库函数不能内联
     * 2. 禁止直接递归（fib 内不展开 fib）→ 防止无限循环
     * 3. 允许跨函数内联递归函数（main 可以内联 fib）
     * 4. 依靠 MAX_CALLER_SIZE 和外层 MAX_INLINE_ITERATIONS 控制
     */
    private boolean canInline(Function callee, Function caller) {
        // 1. 硬性限制：库函数不能内联
        if (callee.isDeclaration()) return false;
        
        // 2.【关键】禁止直接递归，防止无限循环
        // fib 内不展开 fib，但 main 可以内联 fib
        if (callee == caller) return false;
        
        // 3.【激进】允许跨函数内联自递归函数
        // main 调用 fib(4) 时，fib 被内联到 main
        // 内联后产生的 fib(3), fib(2) 调用会在下一轮继续处理
        // 因为此时 caller 是 main，callee 是 fib，不触发上面的限制
        
        // 4. Caller 体积保护（防止爆炸的最后一道防线）
        int callerSize = countInstructions(caller);
        if (callerSize > MAX_CALLER_SIZE) return false;
        
        // 5. Callee 大小限制
        int calleeSize = countInstructions(callee);
        return calleeSize < INLINE_THRESHOLD;
    }
    
    /**
     * 统计函数指令数
     */
    private int countInstructions(Function func) {
        int count = 0;
        for (BasicBlock bb : func.getBasicBlocks()) {
            count += bb.getInstructions().size();
        }
        return count;
    }


    /**
     * 执行内联操作的核心逻辑
     */
    private void performInline(CallInst callInst) {
        Function caller = callInst.getParentBlock().getParentFunction();
        Function callee = callInst.getFunction();
        BasicBlock callBlock = callInst.getParentBlock();
        
        String suffix = "_inl" + (inlineCounter++);

        // --- Step 1: 分裂基本块 ---
        BasicBlock splitBlock = splitBlockAfterCall(callBlock, callInst, caller, suffix);

        // --- Step 2: 克隆被调函数 ---
        Map<Value, Value> valueMap = new HashMap<>();
        Map<BasicBlock, BasicBlock> blockMap = new HashMap<>();
        
        // 2.1 映射参数：形参 -> 实参
        List<Argument> calleeArgs = callee.getArguments();
        List<Value> callArgs = callInst.getArguments();
        for (int i = 0; i < calleeArgs.size(); i++) {
            valueMap.put(calleeArgs.get(i), callArgs.get(i));
        }

        // 2.2 克隆基本块（先创建所有块，建立映射）
        List<BasicBlock> clonedBlocks = new ArrayList<>();
        for (BasicBlock bb : callee.getBasicBlocks()) {
            BasicBlock newBB = new BasicBlock(bb.getName() + suffix, null);
            newBB.setParentFunction(caller);  // [修复] 设置父函数
            clonedBlocks.add(newBB);
            blockMap.put(bb, newBB);
            valueMap.put(bb, newBB);
        }
        
        // 插入克隆的块到 Caller 中 (在 callBlock 和 splitBlock 之间)
        int bbIndex = caller.getBasicBlocks().indexOf(callBlock);
        caller.getBasicBlocks().addAll(bbIndex + 1, clonedBlocks);

        // 2.3 克隆指令（单遍，直接创建完整指令）
        List<ReturnInst> originalReturns = new ArrayList<>();
        Map<Instruction, Instruction> phiMap = new HashMap<>();  // 用于后续修复 Phi

        for (BasicBlock oldBB : callee.getBasicBlocks()) {
            BasicBlock newBB = blockMap.get(oldBB);
            
            for (Instruction oldInst : oldBB.getInstructions()) {
                if (oldInst instanceof ReturnInst ret) {
                    originalReturns.add(ret);
                    continue; // Return 稍后处理
                }
                
                // 直接创建完整的指令（包含操作数）
                Instruction newInst = cloneInstruction(oldInst, newBB, suffix, valueMap, blockMap);
                if (newInst != null) {
                    // 注意：cloneInstruction 使用 newBB 作为 parentBlock，会自动添加到 newBB
                    valueMap.put(oldInst, newInst);
                    
                    // 记录 Phi 用于后续修复
                    if (oldInst instanceof PhiInst) {
                        phiMap.put(oldInst, newInst);
                    }
                }
            }
        }
        
        // 修复 Phi 节点的 incoming 值（可能引用后面定义的指令）
        for (Map.Entry<Instruction, Instruction> entry : phiMap.entrySet()) {
            fixupPhiOperands(entry.getKey(), entry.getValue(), valueMap, blockMap);
        }
        
        // 2.4 CFG 已由 BranchInst 构造函数自动更新，无需手动处理


        // --- Step 3: 连接控制流 ---
        BasicBlock calleeEntry = blockMap.get(callee.getBasicBlocks().get(0));
        
        // 3.1 CallBlock 跳到 Callee Entry
        // [修复] BranchInst 构造函数会自动添加到 parentBlock，不需要手动再添加
        // 同时构造函数也会更新 CFG，不需要手动更新
        new BranchInst(calleeEntry, callBlock);

        // 3.2 处理返回值和出口
        handleReturns(originalReturns, blockMap, valueMap, splitBlock, callInst);

        // --- Step 4: 清理 ---
        callBlock.getInstructions().remove(callInst);
    }

    /**
     * 在调用点之后分裂基本块
     */
    private BasicBlock splitBlockAfterCall(BasicBlock callBlock, CallInst callInst, 
                                            Function caller, String suffix) {
        BasicBlock splitBlock = new BasicBlock(callBlock.getName() + "_cont" + suffix, null);
        splitBlock.setParentFunction(caller);  // [修复] 设置父函数
        
        List<Instruction> instructions = callBlock.getInstructions();
        int callIndex = instructions.indexOf(callInst);
        
        // 将 callInst 之后的指令移动到 splitBlock
        List<Instruction> movedInsts = new ArrayList<>();
        for (int i = callIndex + 1; i < instructions.size(); i++) {
            Instruction inst = instructions.get(i);
            movedInsts.add(inst);
            inst.setParentBlock(splitBlock);
        }
        
        instructions.removeAll(movedInsts);
        splitBlock.getInstructions().addAll(movedInsts);
        
        // 维护 CFG
        splitBlock.getSuccessors().addAll(callBlock.getSuccessors());
        callBlock.getSuccessors().clear();
        
        for (BasicBlock succ : splitBlock.getSuccessors()) {
            succ.getPredecessors().remove(callBlock);
            succ.getPredecessors().add(splitBlock);
            replacePhiPredecessor(succ, callBlock, splitBlock);
        }
        
        // 插入 splitBlock 到函数块列表
        int bbIndex = caller.getBasicBlocks().indexOf(callBlock);
        caller.getBasicBlocks().add(bbIndex + 1, splitBlock);
        
        return splitBlock;
    }

    /**
     * 克隆单条指令（直接创建完整的指令，使用 valueMap 解析操作数）
     */
    private Instruction cloneInstruction(Instruction oldInst, BasicBlock newBB, String suffix,
                                          Map<Value, Value> valueMap, Map<BasicBlock, BasicBlock> blockMap) {
        String newName = oldInst.getName() != null ? oldInst.getName() + suffix : null;
        
        if (oldInst instanceof BinaryOpInst bin) {
            Value newLhs = resolveValue(bin.getLhs(), valueMap);
            Value newRhs = resolveValue(bin.getRhs(), valueMap);
            return new BinaryOpInst(bin.getOp(), newLhs, newRhs, newName, newBB);
        }
        if (oldInst instanceof LoadInst load) {
            Value newPtr = resolveValue(load.getPointer(), valueMap);
            return new LoadInst(newPtr, newName, newBB);
        }
        if (oldInst instanceof StoreInst store) {
            Value newVal = resolveValue(store.getValue(), valueMap);
            Value newPtr = resolveValue(store.getPointer(), valueMap);
            return new StoreInst(newVal, newPtr, newBB);
        }
        if (oldInst instanceof AllocaInst alloca) {
            // [优化] 将 Alloca 提升到 Caller 的 Entry Block
            // 这样 Mem2Reg 能更好地工作
            Function caller = newBB.getParentFunction();
            BasicBlock entryBlock = caller.getBasicBlocks().get(0);
            
            AllocaInst newAlloca = new AllocaInst(alloca.getAllocatedType(), newName, entryBlock);
            // 移动到 Entry Block 的指令列表头部
            entryBlock.getInstructions().remove(newAlloca); // 先移除（构造函数自动加到了末尾）
            entryBlock.getInstructions().add(0, newAlloca); // 加到头部
            
            return newAlloca;
        }
        if (oldInst instanceof IcmpInst icmp) {
            Value newLhs = resolveValue(icmp.getLhs(), valueMap);
            Value newRhs = resolveValue(icmp.getRhs(), valueMap);
            return new IcmpInst(icmp.getCmpType(), newLhs, newRhs, newName, newBB);
        }
        if (oldInst instanceof BranchInst br) {
            if (br.isConditional()) {
                Value newCond = resolveValue(br.getOperand(0), valueMap);
                // [修正] 统一使用 resolveValue 而不是 blockMap
                BasicBlock newTrue = (BasicBlock) resolveValue(br.getOperand(1), valueMap);
                BasicBlock newFalse = (BasicBlock) resolveValue(br.getOperand(2), valueMap);
                return new BranchInst(newCond, newTrue, newFalse, newBB);
            } else {
                BasicBlock newTarget = (BasicBlock) resolveValue(br.getOperand(0), valueMap);
                return new BranchInst(newTarget, newBB);
            }
        }
        if (oldInst instanceof GetElementPtrInst gep) {
            Value newBase = resolveValue(gep.getBasePtr(), valueMap);
            List<Value> newIndices = new ArrayList<>();
            for (Value idx : gep.getIndices()) {
                newIndices.add(resolveValue(idx, valueMap));
            }
            return new GetElementPtrInst(newBase, newIndices, newName, newBB);
        }
        if (oldInst instanceof ZextInst zext) {
            Value newVal = resolveValue(zext.getOperand(0), valueMap);
            return new ZextInst(newVal, zext.getType(), newName, newBB);
        }
        if (oldInst instanceof TruncInst trunc) {
            Value newVal = resolveValue(trunc.getOperand(0), valueMap);
            return new TruncInst(newVal, trunc.getType(), newName, newBB);
        }
        if (oldInst instanceof PhiInst phi) {
            // Phi 需要特殊处理：先创建空 Phi，稍后填充
            return new PhiInst(phi.getType(), newName, newBB);
        }
        if (oldInst instanceof CallInst call) {
            List<Value> newArgs = new ArrayList<>();
            for (Value arg : call.getArguments()) {
                newArgs.add(resolveValue(arg, valueMap));
            }
            return new CallInst(call.getFunction(), newArgs, newName, newBB);
        }
        
        return null;
    }

    /**
     * 填充克隆的 Phi 节点的 incoming 值
     * 【关键修复】跳过死路径，防止引入 null 引用或幽灵块
     */
    private void fixupPhiOperands(Instruction oldInst, Instruction newInst,
                                   Map<Value, Value> valueMap, Map<BasicBlock, BasicBlock> blockMap) {
        if (oldInst instanceof PhiInst oldPhi && newInst instanceof PhiInst newPhi) {
            for (int i = 0; i < oldPhi.getOperands().size(); i += 2) {
                Value oldVal = oldPhi.getOperand(i);
                BasicBlock oldBlock = (BasicBlock) oldPhi.getOperand(i + 1);
                
                // 【关键修复】检查块是否被克隆了
                // 如果 oldBlock 是死代码（不可达），它可能不在 blockMap 中
                BasicBlock newBlock = blockMap.get(oldBlock);
                if (newBlock == null) {
                    continue; // 跳过死路径！防止引入幽灵块
                }
                
                Value newVal = resolveValue(oldVal, valueMap);
                // 确保 newValue 也不是 null
                if (newVal != null) {
                    newPhi.addIncoming(newVal, newBlock);
                }
            }
        }
    }

    /**
     * 处理返回指令，连接到 splitBlock
     * 【修复】先过滤死路径，避免 null 块引发问题
     */
    private void handleReturns(List<ReturnInst> returns, Map<BasicBlock, BasicBlock> blockMap,
                               Map<Value, Value> valueMap, BasicBlock splitBlock, CallInst callInst) {
        if (returns.isEmpty()) return;
        
        // 【关键修复】先过滤掉死路径的返回
        List<ReturnInst> validReturns = new ArrayList<>();
        for (ReturnInst ret : returns) {
            if (blockMap.containsKey(ret.getParentBlock())) {
                validReturns.add(ret);
            }
        }
        
        // 如果没有有效的返回路径（Callee 内部全是死代码），用 0 替换
        if (validReturns.isEmpty()) {
            if (!callInst.getType().isVoidType()) {
                replaceAllUsesWith(callInst, new ConstantInt(0));
            }
            return;
        }
        
        boolean isVoidReturn = callInst.getType().isVoidType();
        
        if (isVoidReturn) {
            // Void 返回，只需要跳转
            for (ReturnInst ret : validReturns) {
                BasicBlock retBlock = blockMap.get(ret.getParentBlock());
                new BranchInst(splitBlock, retBlock);
            }
        } else if (validReturns.size() == 1) {
            // 单一返回值
            ReturnInst ret = validReturns.get(0);
            BasicBlock retBlock = blockMap.get(ret.getParentBlock());
            Value retVal = resolveValue(ret.getReturnValue(), valueMap);
            
            new BranchInst(splitBlock, retBlock);
            replaceAllUsesWith(callInst, retVal);
        } else {
            // 多返回值 -> 需要 Phi
            PhiInst phi = new PhiInst(callInst.getType(), "inl_ret", null);
            phi.setParentBlock(splitBlock);
            splitBlock.getInstructions().add(0, phi);  // 插入到头部
            
            for (ReturnInst ret : validReturns) {
                BasicBlock retBlock = blockMap.get(ret.getParentBlock());
                Value retVal = resolveValue(ret.getReturnValue(), valueMap);
                
                new BranchInst(splitBlock, retBlock);
                phi.addIncoming(retVal, retBlock);
            }
            
            replaceAllUsesWith(callInst, phi);
        }
    }


    /**
     * 从 map 中获取映射后的值
     */
    private Value resolveValue(Value oldVal, Map<Value, Value> valueMap) {
        if (valueMap.containsKey(oldVal)) {
            return valueMap.get(oldVal);
        }
        return oldVal; // Constant, GlobalVariable 等
    }

    /**
     * 替换 Block 中 Phi 节点的前驱引用
     */
    private void replacePhiPredecessor(BasicBlock bb, BasicBlock oldPred, BasicBlock newPred) {
        for (Instruction inst : bb.getInstructions()) {
            if (inst instanceof PhiInst phi) {
                for (int i = 0; i < phi.getOperands().size(); i += 2) {
                    if (phi.getOperand(i + 1) == oldPred) {
                        phi.setOperand(i + 1, newPred);
                    }
                }
            } else {
                break;
            }
        }
    }

    private void replaceAllUsesWith(Value oldValue, Value newValue) {
        List<Use> usersSnapshot = new ArrayList<>(oldValue.getUsers());
        for (Use use : usersSnapshot) {
            use.setValue(newValue);
        }
    }
}

