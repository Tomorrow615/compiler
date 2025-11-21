package io.github.tomorrow615.compiler.midend.irgen;

import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;

import java.util.List;

public class IRBuilder {
    private Module module;
    private Function currentFunction;
    private BasicBlock currentBlock;

    public IRBuilder() {
    }

    public void setModule(Module module) {
        this.module = module;
    }

    public void setCurrentFunction(Function currentFunction) {
        this.currentFunction = currentFunction;
    }

    public void setInsertPoint(BasicBlock currentBlock) {
        this.currentBlock = currentBlock;
    }

    public Module getModule() {
        return module;
    }

    public Function getCurrentFunction() {
        return currentFunction;
    }

    public BasicBlock getCurrentBlock() {
        return currentBlock;
    }

    public Value createAlloca(Type type, String name) {
        AllocaInst alloca = new AllocaInst(type, name);
        insertToEntryBlock(alloca);
        return alloca;
    }

    public Value createLoad(Value ptr, String name) {
        return new LoadInst(ptr, name, this.currentBlock);
    }

    public Instruction createStore(Value value, Value ptr) {
        return new StoreInst(value, ptr, this.currentBlock);
    }

    public Value createGep(Value basePtr, List<Value> indices, String name) {
        return new GetElementPtrInst(basePtr, indices, name, this.currentBlock);
    }

    public Value createAdd(Value lhs, Value rhs, String name) {
        return new BinaryOpInst(BinaryOpInst.OpCode.ADD, lhs, rhs, name, this.currentBlock);
    }

    public Value createSub(Value lhs, Value rhs, String name) {
        return new BinaryOpInst(BinaryOpInst.OpCode.SUB, lhs, rhs, name, this.currentBlock);
    }

    public Value createMul(Value lhs, Value rhs, String name) {
        return new BinaryOpInst(BinaryOpInst.OpCode.MUL, lhs, rhs, name, this.currentBlock);
    }

    public Value createSdiv(Value lhs, Value rhs, String name) {
        return new BinaryOpInst(BinaryOpInst.OpCode.SDIV, lhs, rhs, name, this.currentBlock);
    }

    public Value createSrem(Value lhs, Value rhs, String name) {
        return new BinaryOpInst(BinaryOpInst.OpCode.SREM, lhs, rhs, name, this.currentBlock);
    }

    public Value createIcmp(IcmpInst.CmpType type, Value lhs, Value rhs, String name) {
        return new IcmpInst(type, lhs, rhs, name, this.currentBlock);
    }

    public Instruction createRet(Value value) {
        if (hasTerminator()) return null;
        return new ReturnInst(value, this.currentBlock);
    }

    public Instruction createRetVoid() {
        if (hasTerminator()) return null;
        return new ReturnInst(this.currentBlock);
    }

    public Instruction createBr(BasicBlock target) {
        if (hasTerminator()) return null;
        return new BranchInst(target, this.currentBlock);
    }

    public Instruction createCondBr(Value cond, BasicBlock trueTarget, BasicBlock falseTarget) {
        if (hasTerminator()) return null;
        return new BranchInst(cond, trueTarget, falseTarget, this.currentBlock);
    }

    public Value createCall(Function func, List<Value> args, String name) {
        return new CallInst(func, args, name, this.currentBlock);
    }

    public PhiInst createPhi(Type type, String name) {
        return new PhiInst(type, name, this.currentBlock);
    }

    public Value createZext(Value value, Type targetType, String name) {
        return new ZextInst(value, targetType, name, this.currentBlock);
    }

    public Value createTrunc(Value value, Type targetType, String name) {
        return new TruncInst(value, targetType, name, this.currentBlock);
    }

    private void insertToEntryBlock(AllocaInst alloca) {
        BasicBlock entryBlock = currentFunction.getBasicBlocks().get(0);
        List<Instruction> instructions = entryBlock.getInstructions();

        int insertionPoint = 0;
        for (Instruction inst : instructions) {
            if (!(inst instanceof AllocaInst)) {
                break;
            }
            insertionPoint++;
        }

        instructions.add(insertionPoint, alloca);
        alloca.setParentBlock(entryBlock);
    }

    private boolean hasTerminator() {
        return currentBlock != null && currentBlock.hasTerminator();
    }
}