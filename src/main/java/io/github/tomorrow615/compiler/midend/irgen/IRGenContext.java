package io.github.tomorrow615.compiler.midend.irgen;

import io.github.tomorrow615.compiler.frontend.symbol.FuncSymbol;
import io.github.tomorrow615.compiler.frontend.symbol.Symbol;
import io.github.tomorrow615.compiler.frontend.symbol.SymbolTable;
import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;
import io.github.tomorrow615.compiler.midend.llvm.value.GlobalVariable;
import io.github.tomorrow615.compiler.midend.llvm.type.*;
import io.github.tomorrow615.compiler.midend.llvm.value.Constant;
import io.github.tomorrow615.compiler.midend.llvm.value.ConstantString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class IRGenContext {
    private final Module module;
    private final IRBuilder builder;
    private final List<SymbolTable> allScopes;

    private SymbolTable currentMetadataScope;
    private SymbolTable currentActualScope;
    private int scopeIndex = 0;

    private Function currentFunction;
    private final Map<String, Function> ioFunctions = new HashMap<>();
    private final Map<String, GlobalVariable> stringConstants = new HashMap<>();

    private final Stack<BasicBlock> loopMergeStack = new Stack<>();
    private final Stack<BasicBlock> loopUpdateStack = new Stack<>();

    private int labelCount = 0;

    public IRGenContext(Module module, List<SymbolTable> allScopes) {
        this.module = module;
        this.allScopes = allScopes;
        this.builder = new IRBuilder();
        this.builder.setModule(module);
        if (!allScopes.isEmpty()) {
            this.currentMetadataScope = allScopes.get(scopeIndex++);
            this.currentActualScope = new SymbolTable(null, this.currentMetadataScope.getScopeId());
        }
        initBuiltInFunctions();
    }

    private void initBuiltInFunctions() {
        // getint
        FunctionType getintType = new FunctionType(IntegerType.i32, new ArrayList<>());
        Function getintFunc = new Function(getintType, "@getint");
        this.module.addFunction(getintFunc);
        this.ioFunctions.put("getint", getintFunc);
        if (currentMetadataScope != null) {
            Symbol getintSym = currentMetadataScope.lookup("getint");
            if (getintSym != null) {
                ((FuncSymbol) getintSym).setLlvmValue(getintFunc);
                currentActualScope.addSymbol(getintSym);
            }
        }

        // putint
        FunctionType putintType = new FunctionType(VoidType.get(), List.of(IntegerType.i32));
        Function putintFunc = new Function(putintType, "@putint");
        this.module.addFunction(putintFunc);
        this.ioFunctions.put("putint", putintFunc);
        if (currentMetadataScope != null) {
            Symbol putintSym = currentMetadataScope.lookup("putint");
            if (putintSym != null)
                currentActualScope.addSymbol(putintSym);
        }

        // putch
        FunctionType putchType = new FunctionType(VoidType.get(), List.of(IntegerType.i32));
        Function putchFunc = new Function(putchType, "@putch");
        this.module.addFunction(putchFunc);
        this.ioFunctions.put("putch", putchFunc);
        if (currentMetadataScope != null) {
            Symbol putchSym = currentMetadataScope.lookup("putch");
            if (putchSym != null)
                currentActualScope.addSymbol(putchSym);
        }

        // putstr
        PointerType i8Ptr = new PointerType(IntegerType.i8);
        FunctionType putstrType = new FunctionType(VoidType.get(), List.of(i8Ptr));
        Function putstrFunc = new Function(putstrType, "@putstr");
        this.module.addFunction(putstrFunc);
        this.ioFunctions.put("putstr", putstrFunc);
        if (currentMetadataScope != null) {
            Symbol putstrSym = currentMetadataScope.lookup("putstr");
            if (putstrSym != null)
                currentActualScope.addSymbol(putstrSym);
        }
    }

    public Module getModule() {
        return module;
    }

    public IRBuilder getBuilder() {
        return builder;
    }

    public SymbolTable getCurrentScope() {
        return currentActualScope;
    }

    public SymbolTable getCurrentMetadataScope() {
        return currentMetadataScope;
    }

    public Function getCurrentFunction() {
        return currentFunction;
    }

    public void setCurrentFunction(Function currentFunction) {
        this.currentFunction = currentFunction;
        this.builder.setCurrentFunction(currentFunction);
    }

    public Map<String, Function> getIoFunctions() {
        return ioFunctions;
    }

    public void enterScope() {
        if (scopeIndex < allScopes.size()) {
            this.currentMetadataScope = this.allScopes.get(scopeIndex++);
        }
        this.currentActualScope = new SymbolTable(this.currentActualScope, this.currentMetadataScope.getScopeId());
    }

    public void exitScope() {
        if (this.currentMetadataScope != null) {
            this.currentMetadataScope = this.currentMetadataScope.getParent();
        }
        if (this.currentActualScope != null) {
            this.currentActualScope = this.currentActualScope.getParent();
        }
    }

    public void pushLoop(BasicBlock mergeBB, BasicBlock updateBB) {
        this.loopMergeStack.push(mergeBB);
        this.loopUpdateStack.push(updateBB);
    }

    public void popLoop() {
        this.loopMergeStack.pop();
        this.loopUpdateStack.pop();
    }

    public BasicBlock getCurrentLoopMergeBB() {
        return this.loopMergeStack.peek();
    }

    public BasicBlock getCurrentLoopUpdateBB() {
        return this.loopUpdateStack.peek();
    }

    public String getNextLabel(String prefix) {
        return prefix + "." + (labelCount++);
    }

    public GlobalVariable getGlobalString(String text) {
        if (stringConstants.containsKey(text)) {
            return stringConstants.get(text);
        }
        int numChars = text.getBytes().length + 1;
        ArrayType stringType = new ArrayType(numChars, IntegerType.i8);
        Constant initializer = new ConstantString(stringType, text);
        String gvName = "@.str." + stringConstants.size();
        GlobalVariable gv = new GlobalVariable(stringType, gvName, initializer);
        this.module.addGlobalVariable(gv);
        this.stringConstants.put(text, gv);
        return gv;
    }
}