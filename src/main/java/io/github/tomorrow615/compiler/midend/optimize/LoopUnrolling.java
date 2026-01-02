package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.analysis.LoopAnalysis;
import io.github.tomorrow615.compiler.midend.analysis.LoopInfo;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.type.IntegerType;
import io.github.tomorrow615.compiler.midend.llvm.value.*;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;

import io.github.tomorrow615.compiler.util.Config;

import java.util.*;

/**
 * 循环展开 Pass (Loop Unrolling) - Final Robust Version
 */
public class LoopUnrolling implements Pass {

    private static final int MAX_TRIP_COUNT = 64; 
    private static final int MAX_TOTAL_INSTRUCTIONS = 4000;

    @Override
    public String getName() {
        return "LoopUnrolling";
    }

    @Override
    public void runOnFunction(Function function) {
        // 仅在激进模式下启用循环展开
        if (!Config.AGGRESSIVE_MODE) return;
        
        if (function.isDeclaration() || function.getBasicBlocks().isEmpty()) return;

         new SimplifyCFG().runOnFunction(function);

        LoopAnalysis loopAnalysis = new LoopAnalysis(function);
        List<LoopInfo> loops = new ArrayList<>(loopAnalysis.getAllLoops());
        
        loops.sort((a, b) -> b.getDepth() - a.getDepth());

        for (LoopInfo loop : loops) {
            if (!loop.isInnermost()) continue;
            if (loop.getLatches().size() != 1 || loop.getExitBlocks().size() != 1) continue;

            tryUnrollLoop(loop, function);
        }
    }

    private void tryUnrollLoop(LoopInfo loop, Function function) {
        if (loop.getBlocks().size() > 3) return;

        // 【关键】预检查指令支持
        if (!canUnroll(loop)) return;

        TripCountInfo tripInfo = analyzeTripCount(loop);
        
        if (tripInfo == null || !tripInfo.isConstant) return;

        int bodySize = countLoopBodyInstructions(loop);
        long totalInsts = (long) tripInfo.tripCount * bodySize;

        if (tripInfo.tripCount <= MAX_TRIP_COUNT && totalInsts <= MAX_TOTAL_INSTRUCTIONS) {
            fullUnroll(loop, tripInfo, function);
        }
    }

    private boolean canUnroll(LoopInfo loop) {
        for (BasicBlock bb : loop.getBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof PhiInst || inst instanceof BranchInst) continue;
                if (!isSupportedInstruction(inst)) return false;
            }
        }
        return true;
    }

    private boolean isSupportedInstruction(Instruction inst) {
        return inst instanceof BinaryOpInst || 
               inst instanceof LoadInst || 
               inst instanceof StoreInst || 
               inst instanceof IcmpInst || 
               inst instanceof CallInst || 
               inst instanceof GetElementPtrInst || 
               inst instanceof ZextInst || 
               inst instanceof TruncInst ||
               inst instanceof AllocaInst; 
    }

    // Phase 2: Trip Count Analysis
    private TripCountInfo analyzeTripCount(LoopInfo loop) {
        BasicBlock header = loop.getHeader();
        
        // [Critical Fix] 如果循环头包含函数调用，拒绝分析
        // 函数调用可能有副作用（如修改全局变量、I/O），条件中的副作用必须每次迭代执行
        // 例如：for (i = 0; i < 2 + add(getint(), 2); i++) 中的 add 和 getint 每次迭代都要调用
        for (Instruction inst : header.getInstructions()) {
            if (inst instanceof CallInst) {
                return null;  // 循环条件有副作用，不能展开
            }
        }
        
        Instruction term = header.getInstructions().isEmpty() ? null : header.getInstructions().get(header.getInstructions().size() - 1);
        if (!(term instanceof BranchInst br) || !br.isConditional()) return null;
        
        // [Critical Fix] 只处理标准形式的循环条件
        // 标准形式：true→body(loop内), false→exit(loop外)
        // 即条件为 true 时继续循环，为 false 时退出
        // 
        // 拒绝处理：
        // 1. 复杂条件（|| 或 && 短路）：分支目标不是直接的 body/exit
        // 2. Break 条件形式（true→exit）：如 for(;;) { if(i==3) break; }
        //    这种情况下 tripCount 计算逻辑完全反转，无法正确分析
        BasicBlock trueTarget = (BasicBlock) br.getOperand(1);
        BasicBlock falseTarget = (BasicBlock) br.getOperand(2);
        BasicBlock exitBlock = loop.getExitBlocks().isEmpty() ? null : loop.getExitBlocks().iterator().next();
        
        // 只允许标准形式：true→body(loop内), false→exit(loop外)
        boolean isStandardLoop = loop.contains(trueTarget) && exitBlock != null && falseTarget == exitBlock;
        if (!isStandardLoop) return null;  // 非标准形式，拒绝分析
        
        Value condition = br.getCondition();
        
        if (condition instanceof IcmpInst neInst && neInst.getCmpType() == IcmpInst.CmpType.NE) {
            Value rhs = neInst.getRhs();
            if (rhs instanceof ConstantInt c && c.getValue() == 0) {
                if (neInst.getLhs() instanceof ZextInst zext) {
                    condition = zext.getOperand(0); 
                }
            }
        }
        
        if (!(condition instanceof IcmpInst icmp)) return null;

        PhiInst iv = findInductionVariable(loop, icmp);
        if (iv == null) return null;

        Integer init = null;
        for (int i = 0; i < iv.getOperands().size(); i += 2) {
            BasicBlock pred = (BasicBlock) iv.getOperand(i + 1);
            if (!loop.contains(pred)) {
                if (iv.getOperand(i) instanceof ConstantInt c) {
                    init = c.getValue();
                }
                break;
            }
        }
        
        Integer limit = null;
        Value lhs = icmp.getLhs();
        Value rhs = icmp.getRhs();
        
        if (lhs == iv && rhs instanceof ConstantInt c) limit = c.getValue();
        else if (rhs == iv && lhs instanceof ConstantInt c) limit = c.getValue();
        
        if (init == null || limit == null) return null;

        Integer step = null;
        for (int i = 0; i < iv.getOperands().size(); i += 2) {
            BasicBlock pred = (BasicBlock) iv.getOperand(i + 1);
            if (loop.contains(pred)) {
                Value nextVal = iv.getOperand(i);
                if (nextVal instanceof BinaryOpInst bin && bin.getOp() == BinaryOpInst.OpCode.ADD) {
                    if (bin.getLhs() == iv && bin.getRhs() instanceof ConstantInt c) step = c.getValue();
                    else if (bin.getRhs() == iv && bin.getLhs() instanceof ConstantInt c) step = c.getValue();
                }
                break;
            }
        }
        
        if (step == null || step == 0) return null;

        int count = 0;
        int curr = init;
        while (count <= MAX_TRIP_COUNT + 5) {
            boolean condMet = evaluateIcmp(icmp.getCmpType(), curr, limit);
            if (lhs != iv) return null; 
            
            if (!condMet) break;
            
            curr += step;
            count++;
        }

        if (count > MAX_TRIP_COUNT) return null;

        TripCountInfo info = new TripCountInfo();
        info.isConstant = true;
        info.tripCount = count;
        info.inductionVar = iv;
        info.initValue = init;
        info.step = step;
        return info;
    }

    private boolean evaluateIcmp(IcmpInst.CmpType type, int lhs, int rhs) {
        return switch (type) {
            case EQ -> lhs == rhs;
            case NE -> lhs != rhs;
            case SLT -> lhs < rhs;
            case SLE -> lhs <= rhs;
            case SGT -> lhs > rhs;
            case SGE -> lhs >= rhs;
        };
    }

    private PhiInst findInductionVariable(LoopInfo loop, IcmpInst icmp) {
        Value lhs = icmp.getLhs();
        Value rhs = icmp.getRhs();
        
        if (lhs instanceof PhiInst phi && phi.getParentBlock() == loop.getHeader()) return phi;
        if (rhs instanceof PhiInst phi && phi.getParentBlock() == loop.getHeader()) return phi;
        
        return null;
    }

    // Phase 3: Unroll
    private void fullUnroll(LoopInfo loop, TripCountInfo tripInfo, Function function) {
        BasicBlock header = loop.getHeader();
        BasicBlock latch = loop.getLatches().iterator().next();
        BasicBlock preHeader = loop.getLoopPredeccessor();
        if (preHeader == null) return;

        BasicBlock exitBlock = loop.getExitBlocks().iterator().next();

        // --- 1. 收集循环体指令 ---
        List<Instruction> bodyInsts = new ArrayList<>();
        collectBodyInsts(header, bodyInsts);
        for (BasicBlock bb : loop.getBlocks()) {
            if (bb != header && bb != latch) collectBodyInsts(bb, bodyInsts);
        }
        if (latch != header) collectBodyInsts(latch, bodyInsts);

        // --- 2. 准备插入点 ---
        int insertPos = preHeader.getInstructions().size();
        if (insertPos > 0 && preHeader.getInstructions().get(insertPos - 1) instanceof BranchInst) {
            insertPos--;
        }

        // --- 3. 迭代展开 ---
        Map<Value, Value> lastIterMap = new HashMap<>();
        Map<PhiInst, Value> phiBaseMap = new HashMap<>();
        
        // 收集 Header Phi 的初始值
        for (Instruction inst : header.getInstructions()) {
            if (inst instanceof PhiInst phi) {
                if (phi == tripInfo.inductionVar) continue;
                for (int i = 0; i < phi.getOperands().size(); i += 2) {
                    if (!loop.contains((BasicBlock) phi.getOperand(i+1))) {
                        phiBaseMap.put(phi, phi.getOperand(i));
                        break;
                    }
                }
            }
        }

        int currentInductionVal = tripInfo.initValue;

        for (int iter = 0; iter < tripInfo.tripCount; iter++) {
            Map<Value, Value> valueMap = new HashMap<>();
            
            // 归纳变量常量化
            valueMap.put(tripInfo.inductionVar, new ConstantInt(currentInductionVal));
            
            // 处理其他 Phi
            for (Map.Entry<PhiInst, Value> entry : phiBaseMap.entrySet()) {
                PhiInst phi = entry.getKey();
                if (iter == 0) {
                    valueMap.put(phi, entry.getValue());
                } else {
                    Value latchVal = getPhiLatchValue(phi, loop);
                    if (latchVal != null) {
                        if (lastIterMap.containsKey(latchVal)) {
                            valueMap.put(phi, lastIterMap.get(latchVal));
                        } else {
                            valueMap.put(phi, latchVal);
                        }
                    }
                }
            }

            // 克隆指令
            for (Instruction origInst : bodyInsts) {
                Instruction newInst = cloneInstruction(origInst, valueMap, iter);
                if (newInst != null) {
                    preHeader.getInstructions().add(insertPos++, newInst);
                    newInst.setParentBlock(preHeader); // 必须设置 Parent
                    valueMap.put(origInst, newInst);
                }
            }
            
            lastIterMap = valueMap;
            currentInductionVal += tripInfo.step;
        }

        // --- 4. 重构 CFG ---
        // 移除 PreHeader 旧跳转
        while (!preHeader.getInstructions().isEmpty()) {
            Instruction last = preHeader.getInstructions().get(preHeader.getInstructions().size() - 1);
            if (last instanceof BranchInst) {
                last.removeUseFromOperands();
                preHeader.getInstructions().remove(last);
            } else {
                break;
            }
        }
        
        // 连接到 Exit
        BranchInst br = new BranchInst(exitBlock, preHeader); 
        if (!preHeader.getInstructions().contains(br)) preHeader.getInstructions().add(br);
        
        preHeader.getSuccessors().clear();
        preHeader.getSuccessors().add(exitBlock);
        
        exitBlock.getPredecessors().remove(header);
        if (!exitBlock.getPredecessors().contains(preHeader)) exitBlock.getPredecessors().add(preHeader);
        header.getPredecessors().remove(preHeader);

        // --- 5. 修复 ExitBlock 的 Phi ---
        List<Instruction> phisToRemove = new ArrayList<>();
        int finalInductionVal = currentInductionVal; // 循环结束后的值

        for (Instruction inst : exitBlock.getInstructions()) {
            if (inst instanceof PhiInst phi) {
                Value replacement = null;
                // 尝试从操作数中推断
                for (int i = 0; i < phi.getOperands().size(); i += 2) {
                    if (loop.contains((BasicBlock) phi.getOperand(i+1))) {
                        Value val = phi.getOperand(i);
                        if (val == tripInfo.inductionVar) replacement = new ConstantInt(finalInductionVal);
                        else if (lastIterMap.containsKey(val)) replacement = lastIterMap.get(val);
                        else if (val instanceof Constant) replacement = val;
                        else replacement = val; // Loop Invariant or Outer Value
                        break;
                    }
                }
                // 兜底：如果上面没找到（比如 LoopInfo 不准），直接检查操作数 0
                if (replacement == null && !phi.getOperands().isEmpty()) {
                     Value val = phi.getOperand(0);
                     if (val == tripInfo.inductionVar) replacement = new ConstantInt(finalInductionVal);
                     else if (lastIterMap.containsKey(val)) replacement = lastIterMap.get(val);
                }

                if (replacement != null) {
                    // 使用 Safe Replace
                    safeReplaceAllUses(phi, replacement);
                    phisToRemove.add(phi);
                }
            }
        }
        for (Instruction phi : phisToRemove) {
            phi.removeUseFromOperands();
            exitBlock.getInstructions().remove(phi);
        }

        // --- 6. 全局僵尸值修复 (Global Zombie Fix) ---
        // 这一步至关重要：扫描所有即将删除的指令，如果它们有 Loop 外部的引用，必须替换！
        
        // 6.1 归纳变量替换
        if (tripInfo.inductionVar != null) {
            safeReplaceAllUses(tripInfo.inductionVar, new ConstantInt(finalInductionVal));
        }

        // 6.2 其他循环内指令替换
        // 【关键修复】
        // 对于 Header Phi 节点，这代表循环携带的变量（如累加器）。
        // 它们的值应该是"最后一次迭代结束时的值"，也就是 latch 传回来的值，而不是最后一次迭代开始时的值。
        // 对于其他普通指令，直接用 lastIterMap 替换即可。
        
        Set<PhiInst> headerPhis = new HashSet<>();
        for (Instruction inst : header.getInstructions()) {
            if (inst instanceof PhiInst phi) headerPhis.add(phi);
        }

        // 先处理 Header Phis
        for (PhiInst phi : headerPhis) {
            if (phi == tripInfo.inductionVar) continue; // 已处理
            
            // [Critical Fix] tripCount=0 时，循环不执行，应该用初始值替换
            if (tripInfo.tripCount == 0) {
                if (phiBaseMap.containsKey(phi)) {
                    safeReplaceAllUses(phi, phiBaseMap.get(phi));
                }
                continue;
            }
            
            Value latchVal = getPhiLatchValue(phi, loop);
            if (latchVal != null) {
                // 如果 latchVal 在 lastIterMap 中，说明它是循环内部计算的值（如 sum + a[i]）
                // 此时用最后一次迭代的计算结果替换 phi 的所有引用
                if (lastIterMap.containsKey(latchVal)) {
                    safeReplaceAllUses(phi, lastIterMap.get(latchVal));
                } else {
                    // 如果 latchVal 不在 map 中（比如是常量或不变量），直接替换
                    safeReplaceAllUses(phi, latchVal);
                }
            }
        }

        // 再处理其他指令
        for (Map.Entry<Value, Value> entry : lastIterMap.entrySet()) {
            Value oldVal = entry.getKey();
            Value newVal = entry.getValue();
            
            if (oldVal == tripInfo.inductionVar) continue; // 已处理
            if (oldVal instanceof PhiInst phi && headerPhis.contains(phi)) continue; // 已专门处理
            
            if (oldVal instanceof Instruction oldInst) {
                // 检查 oldInst 是否有外部引用（不在 loop block 内的引用）
                // 简单起见，我们直接替换所有引用。因为 loop block 马上要删了，替换内部引用无所谓。
                safeReplaceAllUses(oldInst, newVal);
            }
        }

        // --- 7. 清理尸体 ---
        for (BasicBlock bb : loop.getBlocks()) {
            // 先断开所有指令的操作数，防止循环引用导致无法 GC
            for (Instruction inst : bb.getInstructions()) {
                inst.removeUseFromOperands();
            }
            bb.getInstructions().clear(); // 清空指令列表
            function.getBasicBlocks().remove(bb);
        }
    }

    // 辅助方法：安全的 replaceAllUsesWith，防止 ConcurrentModification
    private void safeReplaceAllUses(Value oldVal, Value newVal) {
        if (oldVal == null || newVal == null) return;
        List<Use> users = new ArrayList<>(oldVal.getUsers());
        for (Use use : users) {
            use.setValue(newVal);
        }
    }

    private void collectBodyInsts(BasicBlock bb, List<Instruction> list) {
        for (Instruction inst : bb.getInstructions()) {
            if (inst instanceof PhiInst || inst instanceof BranchInst) continue;
            list.add(inst);
        }
    }

    private Value getPhiLatchValue(PhiInst phi, LoopInfo loop) {
        for (int i = 0; i < phi.getOperands().size(); i += 2) {
            if (loop.contains((BasicBlock) phi.getOperand(i+1))) {
                return phi.getOperand(i);
            }
        }
        return null;
    }

    private Instruction cloneInstruction(Instruction orig, Map<Value, Value> valueMap, int iteration) {
        Value[] ops = new Value[orig.getOperands().size()];
        for (int i = 0; i < ops.length; i++) {
            ops[i] = valueMap.getOrDefault(orig.getOperand(i), orig.getOperand(i));
        }

        String name = orig.getName() == null ? null : orig.getName() + "_u" + iteration;
        BasicBlock parent = null; 

        if (orig instanceof BinaryOpInst bin) return new BinaryOpInst(bin.getOp(), ops[0], ops[1], name, parent);
        if (orig instanceof LoadInst load) return new LoadInst(ops[0], name, parent);
        if (orig instanceof StoreInst store) return new StoreInst(ops[0], ops[1], parent);
        if (orig instanceof IcmpInst icmp) return new IcmpInst(icmp.getCmpType(), ops[0], ops[1], name, parent);
        if (orig instanceof CallInst call) {
            List<Value> args = new ArrayList<>(Arrays.asList(ops).subList(1, ops.length));
            return new CallInst(call.getFunction(), args, name, parent);
        }
        if (orig instanceof GetElementPtrInst gep) {
            List<Value> idx = new ArrayList<>(Arrays.asList(ops).subList(1, ops.length));
            return new GetElementPtrInst(ops[0], idx, name, parent);
        }
        if (orig instanceof ZextInst zext) return new ZextInst(ops[0], zext.getType(), name, parent);
        if (orig instanceof TruncInst trunc) return new TruncInst(ops[0], trunc.getType(), name, parent);
        if (orig instanceof AllocaInst alloca) return new AllocaInst(alloca.getAllocatedType(), name);
        
        return null; 
    }

    private int countLoopBodyInstructions(LoopInfo loop) {
        int c = 0;
        for (BasicBlock bb : loop.getBlocks()) c += bb.getInstructions().size();
        return c;
    }

    private static class TripCountInfo {
        boolean isConstant;
        int tripCount;
        PhiInst inductionVar;
        int initValue;
        int step;
    }
}
