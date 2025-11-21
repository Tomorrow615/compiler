package io.github.tomorrow615.compiler.midend.irgen;

import io.github.tomorrow615.compiler.frontend.symbol.SymbolTable;
import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;
import io.github.tomorrow615.compiler.midend.llvm.value.GlobalVariable;
import io.github.tomorrow615.compiler.midend.llvm.type.ArrayType;
import io.github.tomorrow615.compiler.midend.llvm.type.IntegerType;
import io.github.tomorrow615.compiler.midend.llvm.value.Constant;
import io.github.tomorrow615.compiler.midend.llvm.value.ConstantString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * IR生成过程中的上下文环境
 * 负责持有：Module, Builder, 作用域, 循环栈, 全局资源表
 */
public class IRGenContext {
    // 核心组件
    private final Module module;
    private final IRBuilder builder;
    private final List<SymbolTable> allScopes;

    // 作用域管理
    private SymbolTable currentMetadataScope; // 完整元数据表 (来自 SemanticVisitor)
    private SymbolTable currentActualScope;   // 动态构建的当前可见表 (用于 lookup)
    private int scopeIndex = 0;

    // 函数与全局资源
    private Function currentFunction;
    private final Map<String, Function> ioFunctions = new HashMap<>();
    private final Map<String, GlobalVariable> stringConstants = new HashMap<>();

    // 循环控制栈
    private final Stack<BasicBlock> loopMergeStack = new Stack<>();
    private final Stack<BasicBlock> loopUpdateStack = new Stack<>();

    // 计数器
    private int labelCount = 0;

    public IRGenContext(Module module, List<SymbolTable> allScopes) {
        this.module = module;
        this.allScopes = allScopes;
        this.builder = new IRBuilder();
        this.builder.setModule(module);

        // 初始化全局作用域逻辑
        // 假设 allScopes.get(0) 是全局作用域
        if (!allScopes.isEmpty()) {
            this.currentMetadataScope = allScopes.get(scopeIndex++);
            // 创建一个新的空全局作用域作为起点
            this.currentActualScope = new SymbolTable(null, this.currentMetadataScope.getScopeId());
        }
    }

    // ==================== 核心 Getter ====================

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

    // ==================== 作用域管理 ====================

    public void enterScope() {
        // 1. 切换元数据表 (按顺序从 list 取)
        if (scopeIndex < allScopes.size()) {
            this.currentMetadataScope = this.allScopes.get(scopeIndex++);
        }
        // 2. 创建新的动态表
        SymbolTable newActualScope = new SymbolTable(this.currentActualScope, this.currentMetadataScope.getScopeId());
        this.currentActualScope = newActualScope;
    }

    public void exitScope() {
        // 回退
        if (this.currentMetadataScope != null) {
            this.currentMetadataScope = this.currentMetadataScope.getParent();
        }
        if (this.currentActualScope != null) {
            this.currentActualScope = this.currentActualScope.getParent();
        }
    }

    // ==================== 循环栈管理 ====================

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

    // ==================== Label 生成 ====================

    public String getNextLabel(String prefix) {
        return prefix + "." + (labelCount++);
    }

    // ==================== 字符串常量管理 ====================

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