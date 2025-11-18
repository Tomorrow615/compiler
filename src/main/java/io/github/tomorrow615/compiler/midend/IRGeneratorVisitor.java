package io.github.tomorrow615.compiler.midend;

import io.github.tomorrow615.compiler.frontend.ast.*;
import io.github.tomorrow615.compiler.frontend.ast.decl.*;
import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.ast.func.*;
import io.github.tomorrow615.compiler.frontend.ast.stmt.*;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import io.github.tomorrow615.compiler.frontend.lexer.TokenType;
import io.github.tomorrow615.compiler.frontend.symbol.*;
import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.type.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;
import io.github.tomorrow615.compiler.util.SlotTracker;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Stack;

public class IRGeneratorVisitor {

    private final CompUnitNode astRoot;
    private final Module module;
    private final IRBuilder builder;
    private final List<SymbolTable> allScopes;
    private SymbolTable currentScope;
    private int scopeIndex = 0;
    private final Map<String, Function> ioFunctions = new HashMap<>();
    private Function currentFunction;
    private final Map<String, GlobalVariable> stringConstants = new HashMap<>();
    private final Stack<BasicBlock> loopMergeStack = new Stack<>();
    private final Stack<BasicBlock> loopUpdateStack = new Stack<>();
    private int labelCount = 0;

    private final ExpressionIRVisitor exprVisitor;
    private final StatementIRVisitor stmtVisitor;

    public IRGeneratorVisitor(CompUnitNode astRoot, List<SymbolTable> allScopes) {
        this.astRoot = astRoot;
        this.allScopes = allScopes;
        this.module = new Module();
        this.builder = new IRBuilder();
        this.builder.setModule(this.module);
        this.currentScope = allScopes.get(scopeIndex++);

        this.exprVisitor = new ExpressionIRVisitor(this, this.builder);
        this.stmtVisitor = new StatementIRVisitor(this, this.builder, this.exprVisitor);
    }

    public IRBuilder getBuilder() {
        return this.builder;
    }

    public SymbolTable getCurrentScope() {
        return this.currentScope;
    }

    public Function getCurrentFunction() {
        return this.currentFunction;
    }

    public Map<String, Function> getIoFunctions() {
        return this.ioFunctions;
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

    public String getNextIfThenLabel() {
        return "if.then." + (labelCount++);
    }

    public String getNextIfElseLabel() {
        return "if.else." + (labelCount++);
    }

    public String getNextIfMergeLabel() {
        return "if.merge." + (labelCount++);
    }

    public Module generate() {
        declareIOFunctions();
        for (DeclNode decl : astRoot.getDecls()) {
            visit(decl);
        }
        for (FuncDefNode funcDef : astRoot.getFuncDefs()) {
            visit(funcDef);
        }
        visit(astRoot.getMainFuncDef());
        return this.module;
    }

    private void declareIOFunctions() {
        // declare i32 @getint()
        FunctionType getintType = new FunctionType(IntegerType.i32, new ArrayList<>());
        Function getintFunc = new Function(getintType, "@getint");
        this.module.addFunction(getintFunc);
        this.ioFunctions.put("getint", getintFunc);

        // --- [ 4.8 修复：为 getint 建立桥梁 ] ---
        // 查找 Pass 1 创建的 FuncSymbol
        Symbol getintSym = this.currentScope.lookup("getint");
        if (getintSym instanceof FuncSymbol) {
            ((FuncSymbol) getintSym).setLlvmValue(getintFunc);
        }
        // --- [ 修复结束 ] ---

        // declare void @putint(i32)
        FunctionType putintType = new FunctionType(VoidType.get(), List.of(IntegerType.i32));
        Function putintFunc = new Function(putintType, "@putint");
        this.module.addFunction(putintFunc);
        this.ioFunctions.put("putint", putintFunc);

        // declare void @putch(i32)
        FunctionType putchType = new FunctionType(VoidType.get(), List.of(IntegerType.i32));
        Function putchFunc = new Function(putchType, "@putch");
        this.module.addFunction(putchFunc);
        this.ioFunctions.put("putch", putchFunc);

        // declare void @putstr(i8*)
        PointerType i8Ptr = new PointerType(IntegerType.i8);
        FunctionType putstrType = new FunctionType(VoidType.get(), List.of(i8Ptr));
        Function putstrFunc = new Function(putstrType, "@putstr");
        this.module.addFunction(putstrFunc);
        this.ioFunctions.put("putstr", putstrFunc);

        // --- [ 4.8 修复：为 printf 建立桥梁 ] ---
        // (虽然 printf 由 StatementIRVisitor 特殊处理,
        //  但链接它是一个好习惯)
        Symbol printfSym = this.currentScope.lookup("printf");
        if (printfSym instanceof FuncSymbol) {
            // 我们让 "printf" 符号指向 "putstr" 作为默认的 LLVM Function
            ((FuncSymbol) printfSym).setLlvmValue(putstrFunc);
        }
        // --- [ 修复结束 ] ---
    }

    private void visit(DeclNode node) {
        if (node instanceof ConstDeclNode c) {
            visitGlobalConstDecl(c);
        } else if (node instanceof VarDeclNode v) {
            visitGlobalVarDecl(v);
        }
    }

    private void visitGlobalConstDecl(ConstDeclNode node) {
        for (ConstDefNode def : node.getConstDefs()) {
            // 1. 查找符号 (在全局 scope)
            ValueSymbol symbol = (ValueSymbol) this.currentScope.lookup(def.getIdent().getText());
            Type type;
            Constant initializer; // [修改]

            if (symbol.getDimension() > 0) {
                // [修改] 是数组
                // 2.A. 获取大小 (来自 2.A-修订版)
                int size = symbol.getArraySize();
                // 2.B. 创建数组类型
                type = new ArrayType(size, IntegerType.i32);

                // 2.C. 处理全局常量数组初始化
                if (def.getConstInitVal() != null && def.getConstInitVal().getType() == ConstInitValNode.Type.ARRAY) {
                    List<Constant> initValues = new ArrayList<>();
                    List<ConstExpNode> constExps = def.getConstInitVal().getArrayInit();
                    for (ConstExpNode constExp : constExps) {
                        // [使用 2.A-修订版] 调用 evalConstExp
                        initValues.add(evalConstExp(constExp));
                    }
                    // SysY 规定未赋值的用 0 填充
                    for (int i = constExps.size(); i < size; i++) {
                        initValues.add(new ConstantInt(0));
                    }
                    // [使用 2.B] 创建 ConstantArray
                    initializer = new ConstantArray(type, initValues);
                } else {
                    // 如果 const 数组没有 {..} 初始化 (SysY 语法不允许, 但我们做个保护)
                    initializer = new ConstantArray(type, new ArrayList<>()); // 将打印为 zeroinitializer
                }
            } else {
                // [修改] 是标量 (逻辑不变)
                type = IntegerType.i32;
                initializer = evalConstInit(def.getConstInitVal());
            }

            // 3. 创建 GlobalVariable
            GlobalVariable gv = new GlobalVariable(type, "@" + symbol.getName(), initializer);
            this.module.addGlobalVariable(gv);

            // 4. 执行桥接
            symbol.setLlvmValue(gv);
        }
    }

    private void visitGlobalVarDecl(VarDeclNode node) {
        for (VarDefNode def : node.getVarDefs()) {
            // 1. 查找符号
            ValueSymbol symbol = (ValueSymbol) this.currentScope.lookup(def.getIdent().getText());
            Type type;
            Constant initializer = null; // [修改]

            if (symbol.getDimension() > 0) {
                // [修改] 是数组
                // 2.A. 获取大小
                int size = symbol.getArraySize();
                // 2.B. 创建数组类型
                type = new ArrayType(size, IntegerType.i32);

                // 2.C. 处理全局变量数组初始化
                if (def.getType() == VarDefNode.Type.INITIALIZED && def.getInitVal().getType() == InitValNode.Type.ARRAY) {
                    List<Constant> initValues = new ArrayList<>();
                    // 全局变量的 InitVal -> Exp 必须是 ConstExp
                    List<ExpNode> exps = def.getInitVal().getArrayInit();
                    for (ExpNode exp : exps) {
                        // [使用 2.A-修订版]
                        initValues.add(evalConstExp(new ConstExpNode(exp.getAddExp())));
                    }
                    // 用 0 填充剩余部分
                    for (int i = exps.size(); i < size; i++) {
                        initValues.add(new ConstantInt(0));
                    }
                    // [使用 2.B]
                    initializer = new ConstantArray(type, initValues);
                } else {
                    // 未初始化的全局数组
                    initializer = null; // 将在 GlobalVariable.toString() [cite: 2280-2288] 中变为 zeroinitializer
                }
            } else {
                // [修改] 是标量
                type = IntegerType.i32;
                if (def.getType() == VarDefNode.Type.INITIALIZED) {
                    initializer = evalInit(def.getInitVal());
                } else {
                    // 未初始化的全局变量默认为 0
                    initializer = new ConstantInt(0);
                }
            }

            // 3. 创建 GlobalVariable
            GlobalVariable gv = new GlobalVariable(type, "@" + symbol.getName(), initializer);
            this.module.addGlobalVariable(gv);

            // 4. 执行桥接
            symbol.setLlvmValue(gv);
        }
    }

    private Constant evalConstInit(ConstInitValNode node) {
        if (node.getType() == ConstInitValNode.Type.SINGLE) {
            // 递归评估常量表达式
            return evalConstExp(node.getSingleInit());
        } else {
            // TODO: 实现全局数组初始化 (阶段 5)
            // 暂用 0 填充
            return new ConstantInt(0);
        }
    }

    private Constant evalInit(InitValNode node) {
        if (node.getType() == InitValNode.Type.SINGLE) {
            // 全局变量的 InitVal -> Exp -> AddExp
            // 它必须是 ConstExp
            return evalConstExp(new ConstExpNode(node.getSingleInit().getAddExp()));
        } else {
            // TODO: 实现全局数组初始化 (阶段 5)
            return new ConstantInt(0);
        }
    }

    /**
     * (辅助方法) 编译期常量折叠 (简化版)
     * TODO: 扩展此方法以处理 AddExp, MulExp 等 [cite: 649-652, 683-686]
     */
    private Constant evalConstExp(ConstExpNode node) {
        // 简化版：我们假设 ConstExp 只有一个 PrimaryExp [cite: 690-698]，且是 Number [cite: 687-689]
        // 这是一个临时的简化，以便 4.x 阶段可以运行
        try {
            AddExpNode addExp = node.getAddExp();
            MulExpNode mulExp = addExp.getMulExps().get(0);
            UnaryExpNode unaryExp = mulExp.getUnaryExps().get(0);
            PrimaryExpNode primaryExp = unaryExp.getPrimaryExp();
            if (primaryExp.getType() == PrimaryExpNode.Type.NUMBER) {
                String numStr = primaryExp.getNumber().getIntConst().getText();
                return new ConstantInt(Integer.parseInt(numStr));
            }
            // TODO: 真正的常量折叠需要递归 visitAddExp, visitMulExp 等
        } catch (Exception e) {
            // 捕获所有转型和索引错误，说明表达式比我们假设的要复杂
        }

        // 默认返回 0
        return new ConstantInt(0);
    }

    public void enterScope() {
        this.currentScope = this.allScopes.get(scopeIndex++);
    }
    public void exitScope() {
        this.currentScope = this.currentScope.getParent();
    }

    public void visit(FuncDefNode node) {
        FuncSymbol funcSymbol = (FuncSymbol) this.currentScope.lookup(node.getIdent().getText());
        Type retType = (funcSymbol.getReturnType() == SymbolType.IntFunc) ? IntegerType.i32 : VoidType.get();

        List<Type> paramTypes = new ArrayList<>();
        for (ValueSymbol paramSym : funcSymbol.getParameters()) {
            if (paramSym.getDimension() > 0) {
                // 数组参数被视为指针 i32*
                paramTypes.add(new PointerType(IntegerType.i32));
            } else {
                // 标量参数 i32
                paramTypes.add(IntegerType.i32);
            }
        }
        FunctionType funcType = new FunctionType(retType, paramTypes);
        Function function = new Function(funcType, "@" + funcSymbol.getName());
        this.module.addFunction(function);
        this.currentFunction = function;
        builder.setCurrentFunction(function);
        funcSymbol.setLlvmValue(function);

        BasicBlock entryBB = new BasicBlock("entry", function);
        builder.setInsertPoint(entryBB);
        enterScope();
        visitFuncParams(funcSymbol, function);

        visit(node.getBlock());

        // 9. [修改] 仅在当前块*未*终结时添加默认 ret
        boolean hasTerminator = false;
        if (!builder.getCurrentBlock().getInstructions().isEmpty()) {
            Instruction lastInst = builder.getCurrentBlock().getInstructions().get(builder.getCurrentBlock().getInstructions().size() - 1);
            // (未来这里还要检查 BranchInst)
            if (lastInst instanceof ReturnInst) {
                hasTerminator = true;
            }
        }

        if (!hasTerminator) {
            if (retType.isVoidType()) {
                builder.createRetVoid();
            } else {
                builder.createRet(new ConstantInt(0));
            }
        }

        exitScope();
        this.currentFunction = null;
        builder.setCurrentFunction(null);
    }

    public void visit(MainFuncDefNode node) {
        FuncSymbol funcSymbol = new FuncSymbol("main", SymbolType.IntFunc, node.getLineNumber());
        FunctionType funcType = new FunctionType(IntegerType.i32, new ArrayList<>());
        Function function = new Function(funcType, "@main");
        this.module.addFunction(function);
        this.currentFunction = function;
        builder.setCurrentFunction(function);
        funcSymbol.setLlvmValue(function);

        BasicBlock entryBB = new BasicBlock("entry", function);
        builder.setInsertPoint(entryBB);
        enterScope();

        visit(node.getBlock());

        // --- [ 修复开始 ] ---
        // 9. [修改] 仅在当前块*未*终结时添加默认 ret 0
        boolean hasTerminator = false;
        if (!builder.getCurrentBlock().getInstructions().isEmpty()) {
            Instruction lastInst = builder.getCurrentBlock().getInstructions().get(builder.getCurrentBlock().getInstructions().size() - 1);
            if (lastInst instanceof ReturnInst) {
                hasTerminator = true;
            }
        }

        if (!hasTerminator) {
            builder.createRet(new ConstantInt(0));
        }
        // --- [ 修复结束 ] ---

        exitScope();
        this.currentFunction = null;
        builder.setCurrentFunction(null);
    }

    private void visitFuncParams(FuncSymbol funcSymbol, Function function) {
        List<ValueSymbol> paramSymbols = funcSymbol.getParameters();
        List<Argument> arguments = function.getArguments();

        for (int i = 0; i < paramSymbols.size(); i++) {
            ValueSymbol paramSym = paramSymbols.get(i);
            Argument arg = arguments.get(i); // LLVM 的 %0 (i32) 或 %1 (i32*)

            // 1. [修改] 为参数在栈上分配空间
            // arg.getType() 现在可能是 i32 (标量) 或 i32* (数组指针)
            Type paramType = arg.getType();
            // ptr 的类型是 i32* (标量) 或 i32** (数组指针)
            Value ptr = builder.createAlloca(paramType, paramSym.getName() + ".addr");

            // 2. 将参数值 (%0 或 %1) 存入栈空间
            // store i32 %0, i32* %ptr (标量)
            // store i32* %1, i32** %ptr (数组)
            builder.createStore(arg, ptr);

            // 3. [关键] 桥接：让符号表中的 "a" 指向 "%a.addr"
            paramSym.setLlvmValue(ptr);
        }
    }

    public void visit(BlockNode node) {
        for (BlockItemNode item : node.getBlockItems()) {
            visit(item);
        }
    }


    private void visit(BlockItemNode node) {
        if (node instanceof DeclNode d) {
            // 局部变量声明 (不变)
            visitLocalDecl(d);
        } else if (node instanceof StmtNode s) {
            // --- [ 修改开始 ] ---
            if (s instanceof BlockNode b) {
                // 如果是嵌套块，Hub 必须处理作用域
                enterScope();
                visit(b); // 递归调用 hub.visit(BlockNode)
                exitScope();
            } else {
                // 如果是其他语句(Assign, Return...)，委托给 stmtVisitor
                stmtVisitor.visit(s);
            }
            // --- [ 修改结束 ] ---
        }
    }

    private void visitLocalDecl(DeclNode node) {
        if (node instanceof ConstDeclNode c) {
            visitLocalConstDecl(c);
        } else if (node instanceof VarDeclNode v) {
            visitLocalVarDecl(v);
        }
    }

    // 位于 midend/IRGeneratorVisitor.java

    private void visitLocalConstDecl(ConstDeclNode node) {
        for (ConstDefNode def : node.getConstDefs()) {
            ValueSymbol symbol = (ValueSymbol) this.currentScope.lookup(def.getIdent().getText());
            Type type;
            if (symbol.getDimension() > 0) {
                int size = symbol.getArraySize();
                type = new ArrayType(size, IntegerType.i32);
            } else {
                type = IntegerType.i32;
            }
            Value ptr = builder.createAlloca(type, symbol.getName() + ".addr");
            symbol.setLlvmValue(ptr);

            // --- [ START 5.2-Array: Local Const Array Init ] ---
            if (symbol.getDimension() > 0) {
                // 是数组， const 必须有 { ... } 初始化
                ConstInitValNode initValNode = def.getConstInitVal();
                if (initValNode.getType() == ConstInitValNode.Type.ARRAY) { //
                    List<ConstExpNode> initExps = initValNode.getArrayInit();

                    for (int i = 0; i < initExps.size(); i++) {
                        // 1. 获取初始值 (e.g., visit(1), visit(2), ...)
                        Value val_i = exprVisitor.visit(initExps.get(i));

                        // 2. 获取 &b[i] 的指针
                        Value zero = new ConstantInt(0);
                        Value i_const = new ConstantInt(i);
                        Value elemPtr = builder.createGep(ptr, List.of(zero, i_const), "init.idx");

                        // 3. store
                        builder.createStore(val_i, elemPtr);
                    }

                    // [SysY 规定: const 数组未赋值的置 0] [cite: 2140]
                    int size = symbol.getArraySize();
                    if (initExps.size() < size) {
                        Value zeroVal = new ConstantInt(0);
                        Value zeroIdx = new ConstantInt(0);
                        for (int i = initExps.size(); i < size; i++) {
                            Value i_const = new ConstantInt(i);
                            Value elemPtr = builder.createGep(ptr, List.of(zeroIdx, i_const), "init.zero.idx");
                            builder.createStore(zeroVal, elemPtr);
                        }
                    }
                }
            } else {
                // 是标量 (保持原逻辑)
                Value initVal = exprVisitor.visit(def.getConstInitVal());
                if (initVal != null) {
                    builder.createStore(initVal, ptr);
                }
            }
            // --- [ END 5.2-Array: Local Const Array Init ] ---
        }
    }

    // 位于 midend/IRGeneratorVisitor.java

    private void visitLocalVarDecl(VarDeclNode node) {
        for (VarDefNode def : node.getVarDefs()) {
            ValueSymbol symbol = (ValueSymbol) this.currentScope.lookup(def.getIdent().getText());
            Type type;
            if (symbol.getDimension() > 0) {
                int size = symbol.getArraySize();
                type = new ArrayType(size, IntegerType.i32);
            } else {
                type = IntegerType.i32;
            }
            Value ptr = builder.createAlloca(type, symbol.getName() + ".addr");
            symbol.setLlvmValue(ptr);

            if (def.getType() == VarDefNode.Type.INITIALIZED) {

                // --- [ START 5.2-Array: Local Array Init ] ---
                if (symbol.getDimension() > 0) {
                    // 是数组，且有 { ... } 初始化
                    InitValNode initValNode = def.getInitVal();
                    if (initValNode.getType() == InitValNode.Type.ARRAY) { // [cite: 1789-1793]
                        List<ExpNode> initExps = initValNode.getArrayInit();

                        for (int i = 0; i < initExps.size(); i++) {
                            // 1. 获取初始值 (e.g., visit(1), visit(2), ...)
                            Value val_i = exprVisitor.visit(initExps.get(i));

                            // 2. 获取 &b[i] 的指针
                            Value zero = new ConstantInt(0);
                            Value i_const = new ConstantInt(i);
                            Value elemPtr = builder.createGep(ptr, List.of(zero, i_const), "init.idx");

                            // 3. store
                            builder.createStore(val_i, elemPtr);
                        }
                        // TODO: SysY 规定局部数组未初始化的部分 *不* 需要自动置 0 [cite: 2143]
                        // (与全局 [cite: 2140] 和 const [cite: 2140] 不同)
                    }
                } else {
                    // 是标量 (保持原逻辑)
                    Value initVal = exprVisitor.visit(def.getInitVal());
                    if (initVal != null) {
                        builder.createStore(initVal, ptr);
                    }
                }
                // --- [ END 5.2-Array: Local Array Init ] ---
            }
        }
    }
}