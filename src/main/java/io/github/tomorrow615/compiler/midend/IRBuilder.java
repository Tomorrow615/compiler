package io.github.tomorrow615.compiler.midend;

import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;

import java.util.List;

/**
 * IRBuilder "画笔"
 * 封装了指令创建并自动插入到 BasicBlock 的逻辑。
 */
public class IRBuilder {

    private Module module;
    private Function currentFunction;
    private BasicBlock currentBlock;

    public IRBuilder() {
        // 初始为空，由 IRGeneratorVisitor 填充
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
        return new AllocaInst(type, name, this.currentBlock);
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
        return new ReturnInst(value, this.currentBlock);
    }

    public Instruction createRetVoid() {
        return new ReturnInst(this.currentBlock);
    }

    public Instruction createBr(BasicBlock target) {
        return new BranchInst(target, this.currentBlock);
    }

    public Instruction createCondBr(Value cond, BasicBlock trueTarget, BasicBlock falseTarget) {
        return new BranchInst(cond, trueTarget, falseTarget, this.currentBlock);
    }

    public Value createCall(Function func, List<Value> args, String name) {
        return new CallInst(func, args, name, this.currentBlock);
    }

    public PhiInst createPhi(Type type, String name) {
        // 注意：PHI 指令必须是 BB 的第一条指令
        // 为简单起见，我们先在末尾添加，后续优化时再调整
        return new PhiInst(type, name, this.currentBlock);
    }


    public Value createZext(Value value, Type targetType, String name) {
        return new ZextInst(value, targetType, name, this.currentBlock);
    }

    public Value createTrunc(Value value, Type targetType, String name) {
        return new TruncInst(value, targetType, name, this.currentBlock);
    }
}
