package io.github.tomorrow615.compiler.frontend.visitor;

import io.github.tomorrow615.compiler.frontend.ast.*;
import io.github.tomorrow615.compiler.frontend.ast.decl.*;
import io.github.tomorrow615.compiler.frontend.ast.func.*;
import io.github.tomorrow615.compiler.frontend.ast.stmt.*;
import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.error.ErrorReporter;
import io.github.tomorrow615.compiler.frontend.lexer.*;
import io.github.tomorrow615.compiler.frontend.symbol.*;

import java.util.ArrayList;
import java.util.List;

public class SemanticVisitor {
    private SymbolTable currentScope;
    private final List<SymbolTable> allScopes = new ArrayList<>();
    private int nextScopeId = 1;
    private FuncSymbol currentFunction;
    private int loopDepth = 0;
    private final ExpressionVisitor exprVisitor;
    private final StatementVisitor stmtVisitor;

    public SemanticVisitor() {
        this.exprVisitor = new ExpressionVisitor(this);
        this.stmtVisitor = new StatementVisitor(this);
    }

    public SymbolTable getCurrentScope() {
        return currentScope;
    }

    public FuncSymbol getCurrentFunction() {
        return currentFunction;
    }

    public int getLoopDepth() {
        return loopDepth;
    }

    public void incrementLoopDepth() {
        this.loopDepth++;
    }

    public void decrementLoopDepth() {
        this.loopDepth--;
    }

    public void setCurrentFunction(FuncSymbol func) {
        this.currentFunction = func;
    }

    public List<SymbolTable> getAllScopes() {
        return allScopes;
    }

    public void enterScope() {
        SymbolTable newScope = new SymbolTable(currentScope, nextScopeId++);
        allScopes.add(newScope);
        currentScope = newScope;
    }

    public void exitScope() {
        if (currentScope != null) {
            currentScope = currentScope.getParent();
        }
    }

    // 编译单元 CompUnit → {Decl} {FuncDef} MainFuncDef
    public void visit(CompUnitNode compUnit) {
        enterScope(); // 创建并进入全局作用域 (id=1)

        for (DeclNode decl : compUnit.getDecls()) {
            visitDecl(decl);
        }

        for (FuncDefNode funcDef : compUnit.getFuncDefs()) {
            visitFuncDef(funcDef);
        }

        visitMainFuncDef(compUnit.getMainFuncDef());
    }

    // 声明 Decl → ConstDecl | VarDecl
    public void visitDecl(DeclNode node) {
        if (node instanceof ConstDeclNode c) {
            visitConstDecl(c);
        } else if (node instanceof VarDeclNode v) {
            visitVarDecl(v);
        }
    }

    // 常量声明 ConstDecl → 'const' BType ConstDef { ',' ConstDef } ';'
    private void visitConstDecl(ConstDeclNode node) {
        for (ConstDefNode def : node.getConstDefs()) {
            visitConstDef(def);
        }
    }

    // 常量定义 ConstDef → Ident [ '[' ConstExp ']' ] '=' ConstInitVal // b
    private void visitConstDef(ConstDefNode node) {
        Token ident = node.getIdent();
        // 维度 > 0 表示数组，否则为标量
        int dimension = node.getConstExps().size();

        SymbolType type = (dimension > 0) ?
                SymbolType.ConstIntArray : SymbolType.ConstInt;

        ValueSymbol symbol = new ValueSymbol(ident.getText(), type, ident.getLineNumber(), dimension);

        boolean success = currentScope.addSymbol(symbol);
        if (!success) {
            ErrorReporter.addError(ident.getLineNumber(), 'b');
        }
    }

    // 变量声明 VarDecl → [ 'static' ] BType VarDef { ',' VarDef } ';'
    private void visitVarDecl(VarDeclNode node) {
        boolean isStatic = node.isStatic();
        for (VarDefNode def : node.getVarDefs()) {
            visitVarDef(def, isStatic);
        }
    }

    // 变量定义 VarDef → Ident [ '[' ConstExp ']' ] | Ident [ '[' ConstExp ']' ] '=' InitVal // b
    private void visitVarDef(VarDefNode node, boolean isStatic) {
        Token ident = node.getIdent();
        int dimension = node.getDimension();

        SymbolType type;
        if (isStatic) {
            type = (dimension > 0) ? SymbolType.StaticIntArray : SymbolType.StaticInt;
        } else {
            type = (dimension > 0) ? SymbolType.IntArray : SymbolType.Int;
        }

        ValueSymbol symbol = new ValueSymbol(ident.getText(), type, ident.getLineNumber(), dimension);

        boolean success = currentScope.addSymbol(symbol);
        if (!success) {
            ErrorReporter.addError(ident.getLineNumber(), 'b');
        }
    }

    // 函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block // b g
    public void visitFuncDef(FuncDefNode node) {
        Token ident = node.getIdent();
        TokenType funcTypeToken = node.getFuncType().getTypeToken().getType();

        SymbolType type = (funcTypeToken == TokenType.VOIDTK) ?
                SymbolType.VoidFunc : SymbolType.IntFunc;

        FuncSymbol funcSymbol = new FuncSymbol(ident.getText(), type, ident.getLineNumber());

        boolean success = currentScope.addSymbol(funcSymbol);
        if (!success) {
            ErrorReporter.addError(ident.getLineNumber(), 'b');
            // 即使重定义，也继续分析函数内部，但使用第一个定义的符号
            Symbol existing = currentScope.lookup(ident.getText());
            if (existing instanceof FuncSymbol) {
                funcSymbol = (FuncSymbol) existing;
            } else {
                // 名字冲突，但不是函数，创建一个临时的继续分析
                funcSymbol = new FuncSymbol(ident.getText(), type, ident.getLineNumber());
            }
        }

        setCurrentFunction(funcSymbol);
        enterScope(); // 进入函数的新作用域

        for (FuncFParamNode paramNode : node.getFuncFParams()) {
            Token paramIdent = paramNode.getIdent();
            boolean isArray = (paramNode.getType() == FuncFParamNode.Type.ARRAY);
            SymbolType paramType = isArray ? SymbolType.IntArray : SymbolType.Int;
            int dimension = isArray ? 1 : 0;

            ValueSymbol paramSymbol = new ValueSymbol(paramIdent.getText(), paramType,
                    paramIdent.getLineNumber(), dimension);

            // 1. 添加到 FuncSymbol 中，用于d,e类错误检查
            funcSymbol.addParameter(paramSymbol);

            // 2. 添加到函数作用域中
            boolean paramSuccess = currentScope.addSymbol(paramSymbol);
            if (!paramSuccess) {
                // 错误 b: 名字重定义 (形参)
                ErrorReporter.addError(paramIdent.getLineNumber(), 'b');
            }
        }

        // 委托 StatementVisitor 处理函数体
        BlockNode funcBody = node.getBlock();
        stmtVisitor.visitBlock(funcBody);


        if (funcSymbol.getReturnType() == SymbolType.IntFunc) {
            boolean hasReturn = false;
            List<BlockItemNode> items = funcBody.getBlockItems();
            if (!items.isEmpty()) {
                BlockItemNode lastItem = items.get(items.size() - 1);
                if (lastItem instanceof ReturnStmtNode) {
                    hasReturn = true;
                }
            }

            if (!hasReturn) {
                ErrorReporter.addError(funcBody.getEndLineNumber(), 'g');
            }
        }

        exitScope(); // 退出函数作用域
        setCurrentFunction(null);
    }

    // 主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block // g
    public void visitMainFuncDef(MainFuncDefNode node) {
        // 创建一个临时的 FuncSymbol 来表示 main，用于 f/g 类错误检查
        FuncSymbol mainSymbol = new FuncSymbol("main", SymbolType.IntFunc, node.getLineNumber());

        setCurrentFunction(mainSymbol);
        enterScope(); // 进入 main 的新作用域

        // 委托 StatementVisitor 处理 main 的函数体
        BlockNode mainBody = node.getBlock();
        stmtVisitor.visitBlock(mainBody);

        // 检查错误 'g'
        if (mainSymbol.getReturnType() == SymbolType.IntFunc) {
            boolean hasReturn = false;
            List<BlockItemNode> items = mainBody.getBlockItems();
            if (!items.isEmpty()) {
                BlockItemNode lastItem = items.get(items.size() - 1);
                if (lastItem instanceof ReturnStmtNode) {
                    hasReturn = true;
                }
            }

            if (!hasReturn) {
                ErrorReporter.addError(mainBody.getEndLineNumber(), 'g');
            }
        }

        exitScope(); // 退出 main 的作用域
        setCurrentFunction(null);
    }

    public ExpressionVisitor getExprVisitor() {
        return exprVisitor;
    }

    public StatementVisitor getStmtVisitor() {
        return stmtVisitor;
    }
}