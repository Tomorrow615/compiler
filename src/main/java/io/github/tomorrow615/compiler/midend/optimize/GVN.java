package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.analysis.DominatorTree;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.*;

/**
 * [Phase 1.4] 全局值编号 (Global Value Numbering) Pass
 * 
 * 相比局部 CSE，GVN 具有以下增强：
 * 1. 全局范围：按支配树 RPO 顺序遍历，跨基本块消除冗余
 * 2. 交换律归一化：add %a, %b 和 add %b, %a 被视为相同表达式
 * 3. 代数化简集成：在查表前进行简单的代数恒等式化简
 */
public class GVN implements Pass {
    
    // 全局表达式哈希表
    private Map<ExpressionKey, Value> exprTable;
    
    // 支配树
    private DominatorTree domTree;
    
    // 待删除的指令
    private Set<Instruction> toRemove;
    
    @Override
    public String getName() {
        return "GVN";
    }
    
    @Override
    public void runOnFunction(Function function) {
        if (function.isDeclaration() || function.getBasicBlocks().isEmpty()) {
            return;
        }
        
        domTree = new DominatorTree(function);
        exprTable = new HashMap<>();
        toRemove = new HashSet<>();
        
        // 按 RPO 顺序遍历基本块
        List<BasicBlock> rpo = computeRPO(function);
        
        for (BasicBlock bb : rpo) {
            processBlock(bb);
        }
        
        // 删除冗余指令
        for (BasicBlock bb : function.getBasicBlocks()) {
            bb.getInstructions().removeIf(toRemove::contains);
        }
    }
    
    /**
     * 计算逆后序 (Reverse Post-Order)
     * RPO 保证处理一个块时，其所有支配者都已被处理
     */
    private List<BasicBlock> computeRPO(Function function) {
        List<BasicBlock> postOrder = new ArrayList<>();
        Set<BasicBlock> visited = new HashSet<>();
        
        // DFS 后序遍历
        if (!function.getBasicBlocks().isEmpty()) {
            dfsPostOrder(function.getBasicBlocks().get(0), visited, postOrder);
        }
        
        // 反转得到 RPO
        Collections.reverse(postOrder);
        return postOrder;
    }
    
    private void dfsPostOrder(BasicBlock bb, Set<BasicBlock> visited, List<BasicBlock> postOrder) {
        if (visited.contains(bb)) return;
        visited.add(bb);
        
        for (BasicBlock succ : bb.getSuccessors()) {
            dfsPostOrder(succ, visited, postOrder);
        }
        
        postOrder.add(bb);
    }
    
    /**
     * 处理单个基本块
     */
    private void processBlock(BasicBlock bb) {
        for (Instruction inst : bb.getInstructions()) {
            if (toRemove.contains(inst)) continue;
            
            // Step 1: 尝试代数化简
            Value simplified = trySimplify(inst);
            if (simplified != null) {
                replaceAllUsesWith(inst, simplified);
                cleanupOperands(inst);
                toRemove.add(inst);
                continue;
            }
            
            // Step 2: 只处理纯计算指令
            if (!isPureComputation(inst)) {
                continue;
            }
            
            // Step 3: 创建表达式键（含交换律归一化）
            ExpressionKey key = makeKey(inst);
            if (key == null) {
                continue;
            }
            
            // Step 4: 查表
            Value existing = exprTable.get(key);
            if (existing != null && dominates(existing, inst)) {
                // 找到可用的公共子表达式
                replaceAllUsesWith(inst, existing);
                cleanupOperands(inst);
                toRemove.add(inst);
            } else {
                // 记录新表达式
                exprTable.put(key, inst);
            }
        }
    }
    
    /**
     * 检查 def 是否支配 use（即 def 在 use 之前可用）
     */
    private boolean dominates(Value def, Instruction use) {
        if (def instanceof Constant || def instanceof Argument) {
            return true; // 常量和参数总是可用
        }
        if (def instanceof Instruction defInst) {
            BasicBlock defBlock = defInst.getParentBlock();
            BasicBlock useBlock = use.getParentBlock();
            
            if (defBlock == useBlock) {
                // 同一块内，检查 def 是否在 use 之前
                List<Instruction> insts = defBlock.getInstructions();
                return insts.indexOf(defInst) < insts.indexOf(use);
            }
            
            // 不同块，使用支配关系
            return domTree.dominates(defBlock, useBlock);
        }
        return false;
    }
    
    // ========== 代数化简 ==========
    
    /**
     * 尝试代数化简，返回化简后的值，如果无法化简返回 null
     */
    private Value trySimplify(Instruction inst) {
        if (inst instanceof BinaryOpInst bin) {
            return simplifyBinary(bin);
        }
        return null;
    }
    
    /**
     * 二元运算的代数化简
     */
    private Value simplifyBinary(BinaryOpInst bin) {
        Value lhs = bin.getLhs();
        Value rhs = bin.getRhs();
        BinaryOpInst.OpCode op = bin.getOp();
        
        // 常量操作数
        boolean lhsIsZero = (lhs instanceof ConstantInt c) && c.getValue() == 0;
        boolean rhsIsZero = (rhs instanceof ConstantInt c) && c.getValue() == 0;
        boolean lhsIsOne = (lhs instanceof ConstantInt c) && c.getValue() == 1;
        boolean rhsIsOne = (rhs instanceof ConstantInt c) && c.getValue() == 1;
        boolean rhsIsMinusOne = (rhs instanceof ConstantInt c) && c.getValue() == -1;
        
        return switch (op) {
            case ADD -> {
                if (lhsIsZero) yield rhs;  // 0 + x = x
                if (rhsIsZero) yield lhs;  // x + 0 = x
                yield null;
            }
            case SUB -> {
                if (rhsIsZero) yield lhs;  // x - 0 = x
                if (lhs == rhs) yield new ConstantInt(0);  // x - x = 0
                yield null;
            }
            case MUL -> {
                if (lhsIsZero || rhsIsZero) yield new ConstantInt(0);  // x * 0 = 0
                if (lhsIsOne) yield rhs;  // 1 * x = x
                if (rhsIsOne) yield lhs;  // x * 1 = x
                yield null;
            }
            case SDIV -> {
                if (rhsIsOne) yield lhs;  // x / 1 = x
                if (lhs == rhs) yield new ConstantInt(1);  // x / x = 1 (除零另外处理)
                yield null;
            }
            case SREM -> {
                if (rhsIsOne) yield new ConstantInt(0);  // x % 1 = 0
                if (lhs == rhs) yield new ConstantInt(0);  // x % x = 0
                yield null;
            }
            case AND -> {
                if (lhsIsZero || rhsIsZero) yield new ConstantInt(0);  // x & 0 = 0
                if (rhsIsMinusOne) yield lhs;  // x & -1 = x
                if (lhs == rhs) yield lhs;  // x & x = x
                yield null;
            }
            case OR -> {
                if (lhsIsZero) yield rhs;  // 0 | x = x
                if (rhsIsZero) yield lhs;  // x | 0 = x
                if (lhs == rhs) yield lhs;  // x | x = x
                yield null;
            }
            case SHL, LSHR, ASHR -> {
                if (rhsIsZero) yield lhs;  // x << 0 = x, x >> 0 = x
                yield null;
            }
            case XOR -> {
                if (rhsIsZero) yield lhs;  // x ^ 0 = x
                if (lhs == rhs) yield new ConstantInt(0);  // x ^ x = 0
                yield null;
            }
        };
    }
    
    // ========== 纯计算判断 ==========
    
    private boolean isPureComputation(Instruction inst) {
        return inst instanceof BinaryOpInst ||
               inst instanceof IcmpInst ||
               inst instanceof GetElementPtrInst ||
               inst instanceof ZextInst ||
               inst instanceof TruncInst;
    }
    
    // ========== 表达式键（含交换律归一化） ==========
    
    private ExpressionKey makeKey(Instruction inst) {
        if (inst instanceof BinaryOpInst bin) {
            return makeBinaryKey(bin);
        }
        if (inst instanceof IcmpInst icmp) {
            return makeIcmpKey(icmp);
        }
        if (inst instanceof GetElementPtrInst gep) {
            List<Value> operands = new ArrayList<>();
            operands.add(gep.getBasePtr());
            operands.addAll(gep.getIndices());
            return new ExpressionKey("gep", operands);
        }
        if (inst instanceof ZextInst zext) {
            return new ExpressionKey("zext", List.of(zext.getOperand(0)));
        }
        if (inst instanceof TruncInst trunc) {
            return new ExpressionKey("trunc", List.of(trunc.getOperand(0)));
        }
        return null;
    }
    
    /**
     * 二元运算键，处理交换律
     */
    private ExpressionKey makeBinaryKey(BinaryOpInst bin) {
        Value lhs = bin.getLhs();
        Value rhs = bin.getRhs();
        String opName = bin.getOp().name();
        
        // 可交换操作：ADD, MUL, AND, OR
        if (isCommutative(bin.getOp())) {
            // 按 hashCode 排序，确保 add %a, %b 和 add %b, %a 生成相同的键
            if (System.identityHashCode(lhs) > System.identityHashCode(rhs)) {
                Value tmp = lhs;
                lhs = rhs;
                rhs = tmp;
            }
        }
        
        return new ExpressionKey("binary_" + opName, List.of(lhs, rhs));
    }
    
    /**
     * 比较运算键，处理交换律
     */
    private ExpressionKey makeIcmpKey(IcmpInst icmp) {
        Value lhs = icmp.getLhs();
        Value rhs = icmp.getRhs();
        IcmpInst.CmpType cmpType = icmp.getCmpType();
        
        // EQ 和 NE 是可交换的
        if (cmpType == IcmpInst.CmpType.EQ || cmpType == IcmpInst.CmpType.NE) {
            if (System.identityHashCode(lhs) > System.identityHashCode(rhs)) {
                Value tmp = lhs;
                lhs = rhs;
                rhs = tmp;
            }
        }
        
        return new ExpressionKey("icmp_" + cmpType.name(), List.of(lhs, rhs));
    }
    
    private boolean isCommutative(BinaryOpInst.OpCode op) {
        return op == BinaryOpInst.OpCode.ADD ||
               op == BinaryOpInst.OpCode.MUL ||
               op == BinaryOpInst.OpCode.AND ||
               op == BinaryOpInst.OpCode.OR;
    }
    
    // ========== 辅助方法 ==========
    
    private void replaceAllUsesWith(Value oldValue, Value newValue) {
        List<Use> usersSnapshot = new ArrayList<>(oldValue.getUsers());
        for (Use use : usersSnapshot) {
            use.setValue(newValue);
        }
    }
    
    private void cleanupOperands(Instruction inst) {
        for (Use use : inst.getOperands()) {
            Value operand = use.getValue();
            if (operand != null) {
                operand.removeUse(use);
            }
        }
    }
    
    // ========== 表达式键类 ==========
    
    private static class ExpressionKey {
        private final String opcode;
        private final List<Value> operands;
        
        public ExpressionKey(String opcode, List<Value> operands) {
            this.opcode = opcode;
            this.operands = new ArrayList<>(operands);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ExpressionKey other)) return false;
            
            if (!opcode.equals(other.opcode)) return false;
            if (operands.size() != other.operands.size()) return false;
            
            for (int i = 0; i < operands.size(); i++) {
                if (operands.get(i) != other.operands.get(i)) {
                    return false;
                }
            }
            return true;
        }
        
        @Override
        public int hashCode() {
            int result = opcode.hashCode();
            for (Value v : operands) {
                result = 31 * result + System.identityHashCode(v);
            }
            return result;
        }
    }
}
