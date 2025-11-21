package io.github.tomorrow615.compiler.midend.irgen;

import io.github.tomorrow615.compiler.frontend.ast.*;
import io.github.tomorrow615.compiler.frontend.ast.decl.*;
import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.ast.func.*;
import io.github.tomorrow615.compiler.frontend.ast.stmt.*;
import io.github.tomorrow615.compiler.frontend.symbol.*;
import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.type.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.ArrayList;
import java.util.List;

public class IRGenerator {
    private final CompUnitNode astRoot;
    private final IRGenContext context;
    private final ConstEvaluator evaluator;
    private final ExpressionGenerator exprGen;
    private final StatementGenerator stmtGen;

    public IRGenerator(CompUnitNode astRoot, List<SymbolTable> allScopes) {
        this.astRoot = astRoot;
        this.context = new IRGenContext(new Module(), allScopes);

        this.evaluator = new ConstEvaluator(context);
        this.exprGen = new ExpressionGenerator(context);
        this.stmtGen = new StatementGenerator(context, exprGen, this);
    }

    public Module generate() {
        for (DeclNode decl : astRoot.getDecls()) {
            visitDecl(decl);
        }
        for (FuncDefNode funcDef : astRoot.getFuncDefs()) {
            visitFuncDef(funcDef);
        }
        visitMainFuncDef(astRoot.getMainFuncDef());
        return context.getModule();
    }

    private void visitFuncDef(FuncDefNode node) {
        FuncSymbol funcSymbol = (FuncSymbol) context.getCurrentMetadataScope().lookup(node.getIdent().getText());
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
        context.getModule().addFunction(function);
        context.setCurrentFunction(function);
        funcSymbol.setLlvmValue(function);
        context.getCurrentScope().addSymbol(funcSymbol);

        BasicBlock entryBB = new BasicBlock("entry", function);
        context.getBuilder().setInsertPoint(entryBB);

        context.enterScope();
        visitFuncParams(funcSymbol, function);
        visitBlock(node.getBlock());
        checkReturn(retType);
        context.exitScope();

        context.setCurrentFunction(null);
    }

    public void visitMainFuncDef(MainFuncDefNode node) {
        FuncSymbol funcSymbol = new FuncSymbol("main", SymbolType.IntFunc, node.getLineNumber());
        FunctionType funcType = new FunctionType(IntegerType.i32, new ArrayList<>());
        Function function = new Function(funcType, "@main");
        context.getModule().addFunction(function);
        context.setCurrentFunction(function);
        funcSymbol.setLlvmValue(function);
        context.getCurrentScope().addSymbol(funcSymbol);

        BasicBlock entryBB = new BasicBlock("entry", function);
        context.getBuilder().setInsertPoint(entryBB);

        context.enterScope();
        visitBlock(node.getBlock());
        checkReturn(IntegerType.i32);
        context.exitScope();

        context.setCurrentFunction(null);
    }

    private void visitFuncParams(FuncSymbol funcSymbol, Function function) {
        List<ValueSymbol> paramSymbols = funcSymbol.getParameters();
        List<Argument> arguments = function.getArguments();
        IRBuilder builder = context.getBuilder();

        for (int i = 0; i < paramSymbols.size(); i++) {
            ValueSymbol paramSym = paramSymbols.get(i);
            Argument arg = arguments.get(i);
            Type paramType = arg.getType();
            Value ptr = builder.createAlloca(paramType, paramSym.getName() + ".addr");
            builder.createStore(arg, ptr);
            paramSym.setLlvmValue(ptr);
            context.getCurrentScope().addSymbol(paramSym);
        }
    }

    private void checkReturn(Type retType) {
        IRBuilder builder = context.getBuilder();
        if (!builder.getCurrentBlock().hasTerminator()) {
            if (retType.isVoidType()) {
                builder.createRetVoid();
            } else {
                builder.createRet(new ConstantInt(0));
            }
        }
    }

    public void visitBlock(BlockNode node) {
        IRBuilder builder = context.getBuilder();
        for (BlockItemNode item : node.getBlockItems()) {
            if (builder.getCurrentBlock().hasTerminator())
                break;
            if (item instanceof DeclNode d) {
                visitDecl(d);
            } else if (item instanceof StmtNode s) {
                stmtGen.visitStmt(s);
            }
        }
    }

    public void visitDecl(DeclNode node) {
        if (node instanceof ConstDeclNode c)
            visitConstDecl(c);
        else if (node instanceof VarDeclNode v)
            visitVarDecl(v);
    }

    private void visitConstDecl(ConstDeclNode node) {
        if (context.getCurrentFunction() == null)
            visitGlobalConstDecl(node);
        else
            visitLocalConstDecl(node);
    }

    private void visitVarDecl(VarDeclNode node) {
        if (node.isStatic())
            visitStaticLocalVarDecl(node); // 静态局部变量当做全局处理逻辑类似
        else if (context.getCurrentFunction() == null)
            visitGlobalVarDecl(node);
        else
            visitLocalVarDecl(node);
    }

    // --- Global / Static ---

    private void visitGlobalConstDecl(ConstDeclNode node) {
        for (ConstDefNode def : node.getConstDefs()) {
            ValueSymbol symbol = (ValueSymbol) context.getCurrentMetadataScope().lookup(def.getIdent().getText());
            handleGlobalInit(symbol, def.getConstInitVal(), true);
            context.getCurrentScope().addSymbol(symbol);
        }
    }

    private void visitGlobalVarDecl(VarDeclNode node) {
        for (VarDefNode def : node.getVarDefs()) {
            ValueSymbol symbol = (ValueSymbol) context.getCurrentMetadataScope().lookup(def.getIdent().getText());
            handleGlobalInit(symbol, def.getInitVal(), false);
            context.getCurrentScope().addSymbol(symbol);
        }
    }

    private void visitStaticLocalVarDecl(VarDeclNode node) {
        for (VarDefNode def : node.getVarDefs()) {
            ValueSymbol symbol = (ValueSymbol) context.getCurrentMetadataScope().lookup(def.getIdent().getText());
            // 生成唯一名称
            String uniqueName = context
                    .getNextLabel("@" + context.getCurrentFunction().getName().substring(1) + "." + symbol.getName());
            // 逻辑与全局变量一致，但使用唯一名
            handleStaticInit(symbol, def.getInitVal(), uniqueName);
            context.getCurrentScope().addSymbol(symbol);
        }
    }

    // --- Local ---

    private void visitLocalConstDecl(ConstDeclNode node) {
        IRBuilder builder = context.getBuilder();
        for (ConstDefNode def : node.getConstDefs()) {
            ValueSymbol symbol = (ValueSymbol) context.getCurrentMetadataScope().lookup(def.getIdent().getText());
            Type type = (symbol.getDimension() > 0)
                    ? new ArrayType(symbol.getArraySize(), IntegerType.i32)
                    : IntegerType.i32;
            Value ptr = builder.createAlloca(type, symbol.getName() + ".addr");
            symbol.setLlvmValue(ptr);

            if (symbol.getDimension() > 0) {
                handleLocalArrayInit(ptr, symbol, def.getConstInitVal(), true);
            } else {
                // 标量：生成 store 指令
                Value initVal = exprGen.visitConstInitVal(def.getConstInitVal());
                if (initVal != null)
                    builder.createStore(initVal, ptr);
                // 编译期求值
                Constant cVal = evalConstInit(def.getConstInitVal());
                if (cVal instanceof ConstantInt ci)
                    symbol.setConstValue(ci.getValue());
            }
            context.getCurrentScope().addSymbol(symbol);
        }
    }

    private void visitLocalVarDecl(VarDeclNode node) {
        IRBuilder builder = context.getBuilder();
        for (VarDefNode def : node.getVarDefs()) {
            ValueSymbol symbol = (ValueSymbol) context.getCurrentMetadataScope().lookup(def.getIdent().getText());
            Type type = (symbol.getDimension() > 0)
                    ? new ArrayType(symbol.getArraySize(), IntegerType.i32)
                    : IntegerType.i32;
            Value ptr = builder.createAlloca(type, symbol.getName() + ".addr");
            symbol.setLlvmValue(ptr);

            if (def.getType() == VarDefNode.Type.INITIALIZED) {
                if (symbol.getDimension() > 0) {
                    handleLocalArrayInit(ptr, symbol, def.getInitVal(), false);
                } else {
                    Value initVal = exprGen.visitInitVal(def.getInitVal());
                    if (initVal != null)
                        builder.createStore(initVal, ptr);
                }
            }
            context.getCurrentScope().addSymbol(symbol);
        }
    }

    // ==================== 初始化辅助方法 ====================

    private void handleGlobalInit(ValueSymbol symbol, ASTNode initValNode, boolean isConst) {
        Type type;
        Constant initializer;
        if (symbol.getDimension() > 0) {
            int size = symbol.getArraySize();
            type = new ArrayType(size, IntegerType.i32);
            List<Constant> initValues = new ArrayList<>();
            List<Integer> intValues = new ArrayList<>(); // for const table

            List<? extends ASTNode> exps = getArrayInitExps(initValNode);
            if (exps != null) {
                for (ASTNode exp : exps) {
                    // 全局/静态变量初始化必须是 ConstExp
                    ConstExpNode constExp = (exp instanceof ConstExpNode) ? (ConstExpNode) exp
                            : new ConstExpNode(((ExpNode) exp).getAddExp());
                    Constant val = new ConstantInt(evaluator.eval(constExp));
                    initValues.add(val);
                    intValues.add(((ConstantInt) val).getValue());
                }
            }
            // 补零
            while (initValues.size() < size) {
                initValues.add(new ConstantInt(0));
                intValues.add(0);
            }
            initializer = new ConstantArray(type, initValues);
            if (isConst)
                symbol.setConstArrayValues(intValues);
        } else {
            type = IntegerType.i32;
            Constant val = evalInitVal(initValNode);
            initializer = val;
            if (isConst && val instanceof ConstantInt ci)
                symbol.setConstValue(ci.getValue());
        }
        GlobalVariable gv = new GlobalVariable(type, "@" + symbol.getName(), initializer);
        context.getModule().addGlobalVariable(gv);
        symbol.setLlvmValue(gv);
    }

    private void handleStaticInit(ValueSymbol symbol, ASTNode initValNode, String uniqueName) {
        // 逻辑与 GlobalInit 基本一致，只是 gv 名字不同
        Type type;
        Constant initializer;
        if (symbol.getDimension() > 0) {
            int size = symbol.getArraySize();
            type = new ArrayType(size, IntegerType.i32);
            List<Constant> initValues = new ArrayList<>();
            List<? extends ASTNode> exps = getArrayInitExps(initValNode);
            if (exps != null) {
                for (ASTNode exp : exps) {
                    ConstExpNode constExp = (exp instanceof ConstExpNode) ? (ConstExpNode) exp
                            : new ConstExpNode(((ExpNode) exp).getAddExp());
                    initValues.add(new ConstantInt(evaluator.eval(constExp)));
                }
            }
            while (initValues.size() < size)
                initValues.add(new ConstantInt(0));
            initializer = new ConstantArray(type, initValues);
        } else {
            type = IntegerType.i32;
            initializer = evalInitVal(initValNode);
        }
        GlobalVariable gv = new GlobalVariable(type, uniqueName, initializer);
        context.getModule().addGlobalVariable(gv);
        symbol.setLlvmValue(gv);
    }

    private void handleLocalArrayInit(Value ptr, ValueSymbol symbol, ASTNode initValNode, boolean isConst) {
        IRBuilder builder = context.getBuilder();
        List<? extends ASTNode> exps = getArrayInitExps(initValNode);
        if (exps == null)
            return;

        List<Integer> intValues = isConst ? new ArrayList<>() : null;
        int size = symbol.getArraySize();

        for (int i = 0; i < exps.size(); i++) {
            ASTNode exp = exps.get(i);
            // 运行时 Store
            Value val;
            if (isConst)
                val = exprGen.visitConstExp((ConstExpNode) exp);
            else
                val = exprGen.visitExpression(exp);

            Value elemPtr = builder.createGep(ptr, List.of(new ConstantInt(0), new ConstantInt(i)), "init.idx");
            builder.createStore(val, elemPtr);

            // 编译期求值 (仅 Const 需要)
            if (isConst) {
                int v = evaluator.eval((ConstExpNode) exp);
                intValues.add(v);
            }
        }
        // 局部数组初始化：如果没填满，需要补零 (SysY语义: 部分初始化其余补0)
        if (exps.size() < size) {
            Value zeroVal = new ConstantInt(0);
            Value zeroIdx = new ConstantInt(0);
            for (int i = exps.size(); i < size; i++) {
                Value elemPtr = builder.createGep(ptr, List.of(zeroIdx, new ConstantInt(i)), "init.zero.idx");
                builder.createStore(zeroVal, elemPtr);
                if (isConst)
                    intValues.add(0);
            }
        }
        if (isConst)
            symbol.setConstArrayValues(intValues);
    }

    // === 适配器方法：处理 ConstInitValNode 和 InitValNode 的差异 ===

    private Constant evalInitVal(ASTNode node) {
        if (node == null)
            return new ConstantInt(0);
        if (node instanceof ConstInitValNode n && n.getType() == ConstInitValNode.Type.SINGLE)
            return new ConstantInt(evaluator.eval(n.getSingleInit()));
        if (node instanceof InitValNode n && n.getType() == InitValNode.Type.SINGLE)
            return new ConstantInt(evaluator.eval(new ConstExpNode(n.getSingleInit().getAddExp()))); // 强制转为 ConstExp 计算
        return new ConstantInt(0);
    }

    private Constant evalConstInit(ConstInitValNode node) {
        if (node != null && node.getType() == ConstInitValNode.Type.SINGLE)
            return new ConstantInt(evaluator.eval(node.getSingleInit()));
        return new ConstantInt(0);
    }

    private List<? extends ASTNode> getArrayInitExps(ASTNode node) {
        if (node instanceof ConstInitValNode n && n.getType() == ConstInitValNode.Type.ARRAY)
            return n.getArrayInit();
        if (node instanceof InitValNode n && n.getType() == InitValNode.Type.ARRAY)
            return n.getArrayInit();
        return null;
    }
}