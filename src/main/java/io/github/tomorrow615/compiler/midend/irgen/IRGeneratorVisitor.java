package io.github.tomorrow615.compiler.midend.irgen;

import io.github.tomorrow615.compiler.frontend.ast.*;
import io.github.tomorrow615.compiler.frontend.ast.decl.*;
import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.ast.func.*;
import io.github.tomorrow615.compiler.frontend.ast.stmt.*;
import io.github.tomorrow615.compiler.frontend.lexer.TokenType;
import io.github.tomorrow615.compiler.frontend.symbol.*;
import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.type.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

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

    // [修改] 引入双作用域
    private SymbolTable currentMetadataScope; // 完整元数据表 (来自 SemanticVisitor)
    private SymbolTable currentActualScope;   // 动态构建的当前可见表 (用于 lookup)

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

        // [修改] 初始化作用域
        // 假设 allScopes.get(0) 是全局作用域
        this.currentMetadataScope = allScopes.get(scopeIndex++);
        // 创建一个新的空全局作用域作为起点
        this.currentActualScope = new SymbolTable(null, this.currentMetadataScope.getScopeId());

        this.exprVisitor = new ExpressionIRVisitor(this, this.builder);
        this.stmtVisitor = new StatementIRVisitor(this, this.builder, this.exprVisitor);
    }

    // ==========================================
    //          Getter & Helper Methods
    // ==========================================

    public String getNextForCondLabel() {
        return "for.cond." + (labelCount++);
    }

    public String getNextForBodyLabel() {
        return "for.body." + (labelCount++);
    }

    public String getNextForUpdateLabel() {
        return "for.update." + (labelCount++);
    }

    public String getNextForMergeLabel() {
        return "for.merge." + (labelCount++);
    }

    public String getNextLorNextLabel() {
        return "lor.next." + (labelCount++);
    }

    public String getNextLandNextLabel() {
        return "land.next." + (labelCount++);
    }

    public String getNextLorTrueLabel() {
        return "lor.true." + (labelCount++);
    }

    public String getNextLorFalseLabel() {
        return "lor.false." + (labelCount++);
    }

    public String getNextLorMergeLabel() {
        return "lor.merge." + (labelCount++);
    }

    public String getNextLandTrueLabel() {
        return "land.true." + (labelCount++);
    }

    public String getNextLandFalseLabel() {
        return "land.false." + (labelCount++);
    }

    public String getNextLandMergeLabel() {
        return "land.merge." + (labelCount++);
    }

    public IRBuilder getBuilder() {
        return this.builder;
    }

    // [修改] 外部查询(如 ExprVisitor)应使用 ActualScope
    public SymbolTable getCurrentScope() {
        return this.currentActualScope;
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

    // ==========================================
    //          Main Generation Logic
    // ==========================================

    public Module generate() {
        declareIOFunctions();
        for (DeclNode decl : astRoot.getDecls()) {
            visitDecl(decl);
        }
        for (FuncDefNode funcDef : astRoot.getFuncDefs()) {
            visitFuncDef(funcDef);
        }
        visitMainFuncDef(astRoot.getMainFuncDef());
        return this.module;
    }

    private void declareIOFunctions() {
        FunctionType getintType = new FunctionType(IntegerType.i32, new ArrayList<>());
        Function getintFunc = new Function(getintType, "@getint");
        this.module.addFunction(getintFunc);
        this.ioFunctions.put("getint", getintFunc);

        // [修改] 从 Metadata 查符号，填入 Actual
        Symbol getintSym = this.currentMetadataScope.lookup("getint");
        if (getintSym instanceof FuncSymbol) {
            ((FuncSymbol) getintSym).setLlvmValue(getintFunc);
            this.currentActualScope.addSymbol(getintSym);
        }

        FunctionType putintType = new FunctionType(VoidType.get(), List.of(IntegerType.i32));
        Function putintFunc = new Function(putintType, "@putint");
        this.module.addFunction(putintFunc);
        this.ioFunctions.put("putint", putintFunc);

        // [修改] 填入 Actual
        Symbol putintSym = this.currentMetadataScope.lookup("putint");
        if (putintSym != null) this.currentActualScope.addSymbol(putintSym);

        FunctionType putchType = new FunctionType(VoidType.get(), List.of(IntegerType.i32));
        Function putchFunc = new Function(putchType, "@putch");
        this.module.addFunction(putchFunc);
        this.ioFunctions.put("putch", putchFunc);

        // [修改] 填入 Actual
        Symbol putchSym = this.currentMetadataScope.lookup("putch");
        if (putchSym != null) this.currentActualScope.addSymbol(putchSym);

        PointerType i8Ptr = new PointerType(IntegerType.i8);
        FunctionType putstrType = new FunctionType(VoidType.get(), List.of(i8Ptr));
        Function putstrFunc = new Function(putstrType, "@putstr");
        this.module.addFunction(putstrFunc);
        this.ioFunctions.put("putstr", putstrFunc);

        // [修改] 从 Metadata 查符号，填入 Actual
        Symbol printfSym = this.currentMetadataScope.lookup("printf");
        if (printfSym instanceof FuncSymbol) {
            ((FuncSymbol) printfSym).setLlvmValue(putstrFunc);
            this.currentActualScope.addSymbol(printfSym);
        }
    }

    // ==========================================
    //          Declarations (Global & Local)
    // ==========================================

    public void visitDecl(DeclNode node) {
        if (node instanceof ConstDeclNode c) {
            visitGlobalConstDecl(c);
        } else if (node instanceof VarDeclNode v) {
            visitGlobalVarDecl(v);
        }
    }

    private void visitGlobalConstDecl(ConstDeclNode node) {
        for (ConstDefNode def : node.getConstDefs()) {
            // 1. [修改] 从 MetadataScope 获取符号信息
            ValueSymbol symbol = (ValueSymbol) this.currentMetadataScope.lookup(def.getIdent().getText());
            Type type;
            Constant initializer;

            if (symbol.getDimension() > 0) {
                int size = symbol.getArraySize();
                type = new ArrayType(size, IntegerType.i32);

                if (def.getConstInitVal() != null && def.getConstInitVal().getType() == ConstInitValNode.Type.ARRAY) {
                    List<Constant> initValues = new ArrayList<>();
                    List<Integer> intValues = new ArrayList<>();

                    List<ConstExpNode> constExps = def.getConstInitVal().getArrayInit();
                    for (ConstExpNode constExp : constExps) {
                        Constant val = evalConstExp(constExp); // evalConstExp 使用 getCurrentScope() 即 ActualScope
                        initValues.add(val);
                        if (val instanceof ConstantInt) {
                            intValues.add(((ConstantInt) val).getValue());
                        } else {
                            intValues.add(0);
                        }
                    }
                    for (int i = constExps.size(); i < size; i++) {
                        initValues.add(new ConstantInt(0));
                        intValues.add(0);
                    }
                    initializer = new ConstantArray(type, initValues);
                    symbol.setConstArrayValues(intValues);
                } else {
                    initializer = new ConstantArray(type, new ArrayList<>());
                }
            } else {
                type = IntegerType.i32;
                initializer = evalConstInit(def.getConstInitVal());
                if (initializer instanceof ConstantInt) {
                    symbol.setConstValue(((ConstantInt) initializer).getValue());
                }
            }

            GlobalVariable gv = new GlobalVariable(type, "@" + symbol.getName(), initializer);
            this.module.addGlobalVariable(gv);
            symbol.setLlvmValue(gv);

            // 2. [关键] 初始化完成后，加入 ActualScope
            this.currentActualScope.addSymbol(symbol);
        }
    }

    private void visitGlobalVarDecl(VarDeclNode node) {
        for (VarDefNode def : node.getVarDefs()) {
            // 1. [修改] 从 MetadataScope 获取符号信息
            ValueSymbol symbol = (ValueSymbol) this.currentMetadataScope.lookup(def.getIdent().getText());
            Type type;
            Constant initializer = null;

            if (symbol.getDimension() > 0) {
                int size = symbol.getArraySize();
                type = new ArrayType(size, IntegerType.i32);

                if (def.getType() == VarDefNode.Type.INITIALIZED && def.getInitVal().getType() == InitValNode.Type.ARRAY) {
                    List<Constant> initValues = new ArrayList<>();
                    List<ExpNode> exps = def.getInitVal().getArrayInit();
                    for (ExpNode exp : exps) {
                        initValues.add(evalConstExp(new ConstExpNode(exp.getAddExp())));
                    }
                    for (int i = exps.size(); i < size; i++) {
                        initValues.add(new ConstantInt(0));
                    }
                    initializer = new ConstantArray(type, initValues);
                } else {
                    initializer = null;
                }
            } else {
                type = IntegerType.i32;
                if (def.getType() == VarDefNode.Type.INITIALIZED) {
                    initializer = evalInit(def.getInitVal());
                } else {
                    initializer = new ConstantInt(0);
                }
            }

            GlobalVariable gv = new GlobalVariable(type, "@" + symbol.getName(), initializer);
            this.module.addGlobalVariable(gv);
            symbol.setLlvmValue(gv);

            // 2. [关键] 初始化完成后，加入 ActualScope
            this.currentActualScope.addSymbol(symbol);
        }
    }

    // ==========================================
    //          Constant Folding Helpers
    // ==========================================

    private Constant evalConstInit(ConstInitValNode node) {
        if (node.getType() == ConstInitValNode.Type.SINGLE) {
            return evalConstExp(node.getSingleInit());
        } else {
            return new ConstantInt(0);
        }
    }

    private Constant evalInit(InitValNode node) {
        if (node.getType() == InitValNode.Type.SINGLE) {
            return evalConstExp(new ConstExpNode(node.getSingleInit().getAddExp()));
        } else {
            return new ConstantInt(0);
        }
    }

    private Constant evalConstExp(ConstExpNode node) {
        return new ConstantInt(calcAddExp(node.getAddExp()));
    }

    // 递归计算辅助方法
    private int calcAddExp(AddExpNode node) {
        int val = calcMulExp(node.getMulExps().get(0));
        for (int i = 0; i < node.getOperators().size(); i++) {
            int rhs = calcMulExp(node.getMulExps().get(i + 1));
            if (node.getOperators().get(i).getType() == TokenType.PLUS) val += rhs;
            else val -= rhs;
        }
        return val;
    }

    private int calcMulExp(MulExpNode node) {
        int val = calcUnaryExp(node.getUnaryExps().get(0));
        for (int i = 0; i < node.getOperators().size(); i++) {
            int rhs = calcUnaryExp(node.getUnaryExps().get(i + 1));
            TokenType op = node.getOperators().get(i).getType();
            if (op == TokenType.MULT) val *= rhs;
            else if (op == TokenType.DIV) val = (rhs != 0) ? val / rhs : 0;
            else if (op == TokenType.MOD) val = (rhs != 0) ? val % rhs : 0;
        }
        return val;
    }

    private int calcUnaryExp(UnaryExpNode node) {
        if (node.getType() == UnaryExpNode.Type.PRIMARY) {
            return calcPrimaryExp(node.getPrimaryExp());
        } else if (node.getType() == UnaryExpNode.Type.UNARY_OP) {
            int val = calcUnaryExp(node.getUnaryExp());
            TokenType op = node.getUnaryOp().getOp().getType();
            if (op == TokenType.MINU) return -val;
            if (op == TokenType.NOT) return (val == 0) ? 1 : 0;
            return val; // PLUS
        }
        return 0;
    }

    private int calcPrimaryExp(PrimaryExpNode node) {
        if (node.getType() == PrimaryExpNode.Type.NUMBER) {
            return Integer.parseInt(node.getNumber().getIntConst().getText());
        } else if (node.getType() == PrimaryExpNode.Type.PAREN_EXP) {
            return calcAddExp(node.getExp().getAddExp());
        } else if (node.getType() == PrimaryExpNode.Type.LVAL) {
            LValNode lval = node.getLval();
            // [关键] 使用 getCurrentScope() (即 ActualScope) 进行查找
            // 这样能确保找到的是已经定义完毕的符号
            Symbol sym = getCurrentScope().lookup(lval.getIdent().getText());

            if (sym instanceof ValueSymbol valSym && valSym.isConst()) {
                if (lval.getType() == LValNode.Type.SCALAR) {
                    if (valSym.getConstValue() != null) {
                        return valSym.getConstValue();
                    }
                } else {
                    ExpNode indexExp = lval.getArrayExps().get(0);
                    int index = calcAddExp(indexExp.getAddExp());

                    Integer val = valSym.getConstArrayValue(index);
                    if (val != null) {
                        return val;
                    }
                }
            }
            return 0;
        }
        return 0;
    }

    // ==========================================
    //          Functions & Scopes
    // ==========================================

    public void enterScope() {
        // 1. 切换元数据表 (按顺序从 list 取)
        this.currentMetadataScope = this.allScopes.get(scopeIndex++);
        // 2. 创建新的动态表
        SymbolTable newActualScope = new SymbolTable(this.currentActualScope, this.currentMetadataScope.getScopeId());
        this.currentActualScope = newActualScope;
    }

    public void exitScope() {
        // 回退
        this.currentMetadataScope = this.currentMetadataScope.getParent();
        this.currentActualScope = this.currentActualScope.getParent();
    }

    public void visitFuncDef(FuncDefNode node) {
        // 1. [修改] 从 MetadataScope 查
        FuncSymbol funcSymbol = (FuncSymbol) this.currentMetadataScope.lookup(node.getIdent().getText());
        Type retType = (funcSymbol.getReturnType() == SymbolType.IntFunc) ? IntegerType.i32 : VoidType.get();

        List<Type> paramTypes = new ArrayList<>();
        for (ValueSymbol paramSym : funcSymbol.getParameters()) {
            if (paramSym.getDimension() > 0) {
                paramTypes.add(new PointerType(IntegerType.i32));
            } else {
                paramTypes.add(IntegerType.i32);
            }
        }
        FunctionType funcType = new FunctionType(retType, paramTypes);
        Function function = new Function(funcType, "@" + funcSymbol.getName());
        this.module.addFunction(function);
        this.currentFunction = function;
        builder.setCurrentFunction(function);
        funcSymbol.setLlvmValue(function);

        // 2. [新增] 将函数符号加入当前(全局) ActualScope
        this.currentActualScope.addSymbol(funcSymbol);

        BasicBlock entryBB = new BasicBlock("entry", function);
        builder.setInsertPoint(entryBB);
        enterScope();
        visitFuncParams(funcSymbol, function);

        visitBlock(node.getBlock());

        boolean hasTerminator = false;
        if (!builder.getCurrentBlock().getInstructions().isEmpty()) {
            Instruction lastInst = builder.getCurrentBlock().getInstructions().get(builder.getCurrentBlock().getInstructions().size() - 1);
            if (lastInst instanceof ReturnInst) {
                hasTerminator = true;
            }
        }

        if (!builder.getCurrentBlock().hasTerminator()) {
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

    public void visitMainFuncDef(MainFuncDefNode node) {
        FuncSymbol funcSymbol = new FuncSymbol("main", SymbolType.IntFunc, node.getLineNumber());
        FunctionType funcType = new FunctionType(IntegerType.i32, new ArrayList<>());
        Function function = new Function(funcType, "@main");
        this.module.addFunction(function);
        this.currentFunction = function;
        builder.setCurrentFunction(function);
        funcSymbol.setLlvmValue(function);

        // [新增] 加入 ActualScope
        this.currentActualScope.addSymbol(funcSymbol);

        BasicBlock entryBB = new BasicBlock("entry", function);
        builder.setInsertPoint(entryBB);
        enterScope();

        visitBlock(node.getBlock());

        boolean hasTerminator = false;
        if (!builder.getCurrentBlock().getInstructions().isEmpty()) {
            Instruction lastInst = builder.getCurrentBlock().getInstructions().get(builder.getCurrentBlock().getInstructions().size() - 1);
            if (lastInst instanceof ReturnInst) {
                hasTerminator = true;
            }
        }

        if (!builder.getCurrentBlock().hasTerminator()) {
            builder.createRet(new ConstantInt(0));
        }

        exitScope();
        this.currentFunction = null;
        builder.setCurrentFunction(null);
    }

    private void visitFuncParams(FuncSymbol funcSymbol, Function function) {
        List<ValueSymbol> paramSymbols = funcSymbol.getParameters();
        List<Argument> arguments = function.getArguments();

        for (int i = 0; i < paramSymbols.size(); i++) {
            ValueSymbol paramSym = paramSymbols.get(i);
            Argument arg = arguments.get(i);

            Type paramType = arg.getType();
            Value ptr = builder.createAlloca(paramType, paramSym.getName() + ".addr");
            builder.createStore(arg, ptr);
            paramSym.setLlvmValue(ptr);

            // [新增] 将参数加入 ActualScope
            this.currentActualScope.addSymbol(paramSym);
        }
    }

    // ==========================================
    //          Local Blocks & Statements
    // ==========================================

    public void visitBlock(BlockNode node) {
        for (BlockItemNode item : node.getBlockItems()) {
            if (builder.getCurrentBlock().hasTerminator()) {
                break;
            }
            visitBlockItem(item);
        }
    }

    private void visitBlockItem(BlockItemNode node) {
        if (node instanceof DeclNode d) {
            visitLocalDecl(d);
        } else if (node instanceof StmtNode s) {
            if (s instanceof BlockNode b) {
                enterScope();
                visitBlock(b);
                exitScope();
            } else {
                stmtVisitor.visitStmt(s);
            }
        }
    }

    private void visitLocalDecl(DeclNode node) {
        if (node instanceof ConstDeclNode c) {
            visitLocalConstDecl(c);
        } else if (node instanceof VarDeclNode v) {
            visitLocalVarDecl(v);
        }
    }

    private void visitLocalConstDecl(ConstDeclNode node) {
        for (ConstDefNode def : node.getConstDefs()) {
            // 1. [修改] 从 MetadataScope 查
            ValueSymbol symbol = (ValueSymbol) this.currentMetadataScope.lookup(def.getIdent().getText());
            Type type;
            if (symbol.getDimension() > 0) {
                int size = symbol.getArraySize();
                type = new ArrayType(size, IntegerType.i32);
            } else {
                type = IntegerType.i32;
            }
            Value ptr = builder.createAlloca(type, symbol.getName() + ".addr");
            symbol.setLlvmValue(ptr);

            if (symbol.getDimension() > 0) {
                ConstInitValNode initValNode = def.getConstInitVal();
                if (initValNode.getType() == ConstInitValNode.Type.ARRAY) {
                    List<ConstExpNode> initExps = initValNode.getArrayInit();
                    List<Integer> intValues = new ArrayList<>();

                    for (int i = 0; i < initExps.size(); i++) {
                        // 生成运行时 store (visitConstExp 使用 ActualScope 进行 lookup)
                        Value val_i = exprVisitor.visitConstExp(initExps.get(i));
                        Value zero = new ConstantInt(0);
                        Value i_const = new ConstantInt(i);
                        Value elemPtr = builder.createGep(ptr, List.of(zero, i_const), "init.idx");
                        builder.createStore(val_i, elemPtr);

                        // 编译期求值 (使用 ActualScope)
                        Constant cVal = evalConstExp(initExps.get(i));
                        if (cVal instanceof ConstantInt) {
                            intValues.add(((ConstantInt) cVal).getValue());
                        } else {
                            intValues.add(0);
                        }
                    }

                    int size = symbol.getArraySize();
                    if (initExps.size() < size) {
                        Value zeroVal = new ConstantInt(0);
                        Value zeroIdx = new ConstantInt(0);
                        for (int i = initExps.size(); i < size; i++) {
                            Value i_const = new ConstantInt(i);
                            Value elemPtr = builder.createGep(ptr, List.of(zeroIdx, i_const), "init.zero.idx");
                            builder.createStore(zeroVal, elemPtr);
                            intValues.add(0);
                        }
                    }
                    symbol.setConstArrayValues(intValues);
                }
            } else {
                // 标量
                Value initVal = exprVisitor.visitConstInitVal(def.getConstInitVal());
                if (initVal != null) {
                    builder.createStore(initVal, ptr);
                }

                Constant cVal = evalConstInit(def.getConstInitVal());
                if (cVal instanceof ConstantInt) {
                    symbol.setConstValue(((ConstantInt) cVal).getValue());
                }
            }

            // 2. [关键] 初始化完毕后，才加入 ActualScope
            this.currentActualScope.addSymbol(symbol);
        }
    }

    private void visitLocalVarDecl(VarDeclNode node) {
        if (node.isStatic()) {
            visitStaticLocalVarDecl(node);
            return;
        }

        for (VarDefNode def : node.getVarDefs()) {
            // 1. [修改] 从 MetadataScope 查
            ValueSymbol symbol = (ValueSymbol) this.currentMetadataScope.lookup(def.getIdent().getText());
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
                if (symbol.getDimension() > 0) {
                    InitValNode initValNode = def.getInitVal();
                    if (initValNode.getType() == InitValNode.Type.ARRAY) {
                        List<ExpNode> initExps = initValNode.getArrayInit();
                        for (int i = 0; i < initExps.size(); i++) {
                            Value val_i = exprVisitor.visitExpression(initExps.get(i));
                            Value zero = new ConstantInt(0);
                            Value i_const = new ConstantInt(i);
                            Value elemPtr = builder.createGep(ptr, List.of(zero, i_const), "init.idx");
                            builder.createStore(val_i, elemPtr);
                        }
                    }
                } else {
                    Value initVal = exprVisitor.visitInitVal(def.getInitVal());
                    if (initVal != null) {
                        builder.createStore(initVal, ptr);
                    }
                }
            }

            // 2. [关键] 初始化完毕后，才加入 ActualScope
            this.currentActualScope.addSymbol(symbol);
        }
    }

    private void visitStaticLocalVarDecl(VarDeclNode node) {
        for (VarDefNode def : node.getVarDefs()) {
            // 1. [修改] 从 MetadataScope 查
            ValueSymbol symbol = (ValueSymbol) this.currentMetadataScope.lookup(def.getIdent().getText());
            Type type;
            Constant initializer = null;

            // ... 静态变量初始化逻辑 (不变) ...
            if (symbol.getDimension() > 0) {
                int size = symbol.getArraySize();
                type = new ArrayType(size, IntegerType.i32);
                if (def.getType() == VarDefNode.Type.INITIALIZED && def.getInitVal().getType() == InitValNode.Type.ARRAY) {
                    List<Constant> initValues = new ArrayList<>();
                    List<ExpNode> exps = def.getInitVal().getArrayInit();
                    for (ExpNode exp : exps) {
                        initValues.add(evalConstExp(new ConstExpNode(exp.getAddExp())));
                    }
                    for (int i = exps.size(); i < size; i++) {
                        initValues.add(new ConstantInt(0));
                    }
                    initializer = new ConstantArray(type, initValues);
                } else {
                    initializer = new ConstantArray(type, new ArrayList<>());
                }
            } else {
                type = IntegerType.i32;
                if (def.getType() == VarDefNode.Type.INITIALIZED) {
                    initializer = evalInit(def.getInitVal());
                } else {
                    initializer = new ConstantInt(0);
                }
            }

            String uniqueName = "@" + currentFunction.getName().substring(1) + "." + symbol.getName() + "." + (labelCount++);
            GlobalVariable gv = new GlobalVariable(type, uniqueName, initializer);
            this.module.addGlobalVariable(gv);
            symbol.setLlvmValue(gv);

            // 2. [关键] 初始化完毕后，加入 ActualScope
            this.currentActualScope.addSymbol(symbol);
        }
    }
}