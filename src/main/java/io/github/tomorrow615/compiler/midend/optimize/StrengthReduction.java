package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.analysis.*;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.*;

/**
 * 强度削减优化 Pass (Strength Reduction)
 * 
 * 将循环中依赖归纳变量的乘法运算转换为累加运算。
 * 
 * 原理：
 *   循环中的 `i * c` 可以转换为初始值 `0` + 每次迭代累加 `c`
 *   
 * 例如：
 *   for (int i = 0; i < n; i++) {
 *       int offset = i * 4;  // 乘法，每次 ~10 周期
 *       a[offset] = ...;
 *   }
 *   
 * 优化后：
 *   int offset = 0;
 *   for (int i = 0; i < n; i++) {
 *       a[offset] = ...;
 *       offset += 4;  // 加法，1 周期
 *   }
 * 
 * 这个优化对于数组遍历特别有效，因为 GEP 指令内部会产生 i * elementSize 的乘法。
 */
public class StrengthReduction implements Pass {
    
    @Override
    public String getName() {
        return "StrengthReduction";
    }
    
    @Override
    public void runOnFunction(Function function) {
        if (function.isDeclaration() || function.getBasicBlocks().isEmpty()) {
            return;
        }
        
        // 构建循环分析
        LoopAnalysis loopAnalysis = new LoopAnalysis(function);
        
        // 从最内层循环开始处理
        List<LoopInfo> loops = new ArrayList<>(loopAnalysis.getAllLoops());
        loops.sort((a, b) -> b.getDepth() - a.getDepth()); // 深度降序
        
        for (LoopInfo loop : loops) {
            reduceStrengthInLoop(loop, function);
        }
    }
    
    /**
     * 对单个循环进行强度削减
     */
    private void reduceStrengthInLoop(LoopInfo loop, Function function) {
        BasicBlock header = loop.getHeader();
        BasicBlock preHeader = loop.getPreHeader();
        
        // 如果没有 PreHeader，尝试获取唯一的非循环前驱
        if (preHeader == null) {
            preHeader = loop.getLoopPredeccessor();
            if (preHeader == null) {
                return; // 无法进行强度削减
            }
        }
        
        // 找到归纳变量
        List<InductionVariable> inductionVars = findInductionVariables(header, loop);
        if (inductionVars.isEmpty()) {
            return;
        }
        
        // 收集循环内所有与归纳变量相关的乘法
        for (InductionVariable iv : inductionVars) {
            reduceMulToAdd(iv, loop, preHeader);
        }
    }
    
    /**
     * 归纳变量信息
     */
    private static class InductionVariable {
        final PhiInst phi;           // Header 中的 Phi
        final Value initialValue;    // 初始值（来自循环外）
        final Value stepValue;       // 步长
        final BinaryOpInst increment; // 递增指令
        
        InductionVariable(PhiInst phi, Value init, Value step, BinaryOpInst inc) {
            this.phi = phi;
            this.initialValue = init;
            this.stepValue = step;
            this.increment = inc;
        }
    }
    
    /**
     * 查找循环中的归纳变量
     */
    private List<InductionVariable> findInductionVariables(BasicBlock header, LoopInfo loop) {
        List<InductionVariable> result = new ArrayList<>();
        
        for (Instruction inst : header.getInstructions()) {
            if (!(inst instanceof PhiInst phi)) {
                break;
            }
            
            Value initVal = null;
            Value loopVal = null;
            BasicBlock initBlock = null;
            BasicBlock loopBlock = null;
            
            for (int i = 0; i < phi.getOperands().size(); i += 2) {
                Value val = phi.getOperand(i);
                BasicBlock blk = (BasicBlock) phi.getOperand(i + 1);
                
                if (loop.contains(blk)) {
                    loopVal = val;
                    loopBlock = blk;
                } else {
                    initVal = val;
                    initBlock = blk;
                }
            }
            
            if (initVal == null || loopVal == null) {
                continue;
            }
            
            // 检查 loopVal 是否是 phi + step 的形式
            if (loopVal instanceof BinaryOpInst bin) {
                if (bin.getOp() == BinaryOpInst.OpCode.ADD) {
                    Value step = null;
                    if (bin.getLhs() == phi && bin.getRhs() instanceof ConstantInt) {
                        step = bin.getRhs();
                    } else if (bin.getRhs() == phi && bin.getLhs() instanceof ConstantInt) {
                        step = bin.getLhs();
                    }
                    
                    if (step != null) {
                        result.add(new InductionVariable(phi, initVal, step, bin));
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * 将循环内的 iv * const 转换为累加形式
     */
    private void reduceMulToAdd(InductionVariable iv, LoopInfo loop, BasicBlock preHeader) {
        PhiInst ivPhi = iv.phi;
        
        // 收集所有使用 iv 进行乘法的指令
        List<BinaryOpInst> mulInstructions = new ArrayList<>();
        
        for (BasicBlock block : loop.getBlocks()) {
            for (Instruction inst : block.getInstructions()) {
                if (inst instanceof BinaryOpInst bin && bin.getOp() == BinaryOpInst.OpCode.MUL) {
                    // 检查是否是 iv * const 或 const * iv
                    ConstantInt constOp = null;
                    boolean ivIsLhs = false;
                    
                    if (bin.getLhs() == ivPhi && bin.getRhs() instanceof ConstantInt c) {
                        constOp = c;
                        ivIsLhs = true;
                    } else if (bin.getRhs() == ivPhi && bin.getLhs() instanceof ConstantInt c) {
                        constOp = c;
                        ivIsLhs = false;
                    }
                    
                    if (constOp != null) {
                        mulInstructions.add(bin);
                    }
                }
            }
        }
        
        if (mulInstructions.isEmpty()) {
            return;
        }
        
        // 对每个乘法进行强度削减
        for (BinaryOpInst mulInst : mulInstructions) {
            performReduction(mulInst, iv, loop, preHeader);
        }
    }
    
    /**
     * 执行单个乘法的强度削减
     * 
     * 将 result = iv * C 转换为：
     * 1. 在 PreHeader: newIV_init = initVal * C
     * 2. 在 Header: newIV = phi(newIV_init, newIV_next)
     * 3. 在循环体: newIV_next = newIV + (step * C)
     * 4. 替换所有 mulInst 的使用为 newIV
     */
    private void performReduction(BinaryOpInst mulInst, InductionVariable iv, 
                                   LoopInfo loop, BasicBlock preHeader) {
        // 获取乘法的常量
        ConstantInt mulConst;
        if (mulInst.getRhs() instanceof ConstantInt c) {
            mulConst = c;
        } else if (mulInst.getLhs() instanceof ConstantInt c) {
            mulConst = c;
        } else {
            return;
        }
        
        int C = mulConst.getValue();
        BasicBlock header = loop.getHeader();
        
        // 计算新的步长: step * C
        int ivStep = ((ConstantInt) iv.stepValue).getValue();
        int newStep = ivStep * C;
        
        // 计算初始值
        Value newInitVal;
        if (iv.initialValue instanceof ConstantInt initConst) {
            // 初始值是常量，直接计算
            newInitVal = new ConstantInt(initConst.getValue() * C);
        } else {
            // 初始值不是常量，需要在 PreHeader 插入乘法
            BinaryOpInst initMul = new BinaryOpInst(
                BinaryOpInst.OpCode.MUL,
                iv.initialValue,
                mulConst,
                mulInst.getName() + "_init",
                null
            );
            // 插入到 PreHeader 的末尾（分支之前）
            insertBeforeTerminator(preHeader, initMul);
            newInitVal = initMul;
        }
        
        // 创建新的 Phi 指令（派生归纳变量）
        PhiInst newPhi = new PhiInst(
            mulInst.getType(),
            mulInst.getName() + "_sr",
            null
        );
        
        // 创建新的递增指令
        BinaryOpInst newIncrement = new BinaryOpInst(
            BinaryOpInst.OpCode.ADD,
            newPhi,
            new ConstantInt(newStep),
            mulInst.getName() + "_next",
            null
        );
        
        // 设置 Phi 的输入
        // 来自 PreHeader 的初始值
        newPhi.addIncoming(newInitVal, preHeader);
        
        // 来自 Latch 的递增值
        BasicBlock latch = loop.getLatches().iterator().next();
        newPhi.addIncoming(newIncrement, latch);
        
        // 将 Phi 插入到 Header 的开头
        header.getInstructions().add(0, newPhi);
        newPhi.setParentBlock(header);
        
        // 将递增指令插入到 Latch 的末尾（分支之前）
        insertBeforeTerminator(latch, newIncrement);
        
        // 替换原乘法指令的所有使用
        replaceAllUsesWith(mulInst, newPhi);
        
        // 删除原乘法指令
        BasicBlock mulBlock = mulInst.getParentBlock();
        if (mulBlock != null) {
            mulBlock.getInstructions().remove(mulInst);
        }
    }
    
    /**
     * 在块的 terminator 之前插入指令
     */
    private void insertBeforeTerminator(BasicBlock block, Instruction inst) {
        List<Instruction> insts = block.getInstructions();
        int insertPos = insts.size() - 1;
        if (insertPos < 0) insertPos = 0;
        insts.add(insertPos, inst);
        inst.setParentBlock(block);
    }
    
    /**
     * 替换 Value 的所有使用
     */
    private void replaceAllUsesWith(Value oldVal, Value newVal) {
        // 收集所有使用者
        List<Use> uses = new ArrayList<>(oldVal.getUsers());
        for (Use use : uses) {
            use.setValue(newVal);
        }
    }
}
