package io.github.tomorrow615.compiler.frontend.visitor;

import io.github.tomorrow615.compiler.frontend.ast.*;
import io.github.tomorrow615.compiler.frontend.ast.decl.*;
import io.github.tomorrow615.compiler.frontend.ast.func.*;
import io.github.tomorrow615.compiler.frontend.ast.stmt.*;
import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.error.ErrorReporter;
import io.github.tomorrow615.compiler.frontend.lexer.*;
import io.github.tomorrow615.compiler.frontend.symbol.*;
import io.github.tomorrow615.compiler.frontend.visitor.*;

public class StatementVisitor {
    private final SemanticVisitor hub;

    public StatementVisitor(SemanticVisitor hub) {
        this.hub = hub;
    }

    // 语句块 Block → '{' { BlockItem } '}'
    // 语句块项 BlockItem → Decl | Stmt
    public void visitBlock(BlockNode node) {
        if (node == null) return;

        for (BlockItemNode item : node.getBlockItems()) {
            if (item instanceof DeclNode d) {
                hub.visitDecl(d);
            } else if (item instanceof StmtNode s) {
                visitStmt(s);
            }
        }
    }

    // 语句 Stmt →
    // | LVal '=' Exp ';' // h
    // | [Exp] ';'
    // | Block
    // | 'if' '(' Cond ')' Stmt [ 'else' Stmt ]
    // | 'for' '(' [ForStmt] ';' [Cond] ';' [ForStmt] ')' Stmt // h
    // | 'break' ';' | 'continue' ';' // m
    // | 'return' [Exp] ';' // f
    // | 'printf''('StringConst {','Exp}')'';' // l
    public void visitStmt(StmtNode node) {
        if (node == null) return;

        if (node instanceof BlockNode b) {
            hub.enterScope();
            visitBlock(b);
            hub.exitScope();
        }
        else if (node instanceof AssignStmtNode a) {
            visitAssignStmt(a);
        }
        else if (node instanceof ExpStmtNode e) {
            visitExpStmt(e);
        }
        else if (node instanceof ForStmtNode f) {
            visitForStmt(f);
        }
        else if (node instanceof BreakStmtNode b) {
            visitBreakStmt(b);
        }
        else if (node instanceof ContinueStmtNode c) {
            visitContinueStmt(c);
        }
        else if (node instanceof ReturnStmtNode r) {
            visitReturnStmt(r);
        }
        else if (node instanceof PrintfStmtNode p) {
            visitPrintfStmt(p);
        }
        else if (node instanceof IfStmtNode i) {
            visitIfStmt(i);
        }
    }

    // | LVal '=' Exp ';' // h
    public void visitAssignStmt(AssignStmtNode node) {
        LValAnalysis lvalInfo = hub.getExprVisitor().visitLVal(node.getlVal());
        // 检查错误 'h'
        if (lvalInfo.symbol() instanceof ValueSymbol) {
            ValueSymbol vs = (ValueSymbol) lvalInfo.symbol();
            if (vs.isConst()) {
                ErrorReporter.addError(node.getLineNumber(), 'h');
            }
        }
        // 未来递归访问 node.getExp() 以推导其类型并检查内部错误
        if (node.getExp() != null) {
            SymbolType expType = hub.getExprVisitor().visitExp(node.getExp());
            // SymbolType lvalType = lvalInfo.resultingType();
        }
    }

    // | [Exp] ';'
    public void visitExpStmt(ExpStmtNode node) {
        if (node.getExp() != null) {
            hub.getExprVisitor().visitExp(node.getExp());
        }
    }

    // | 'for' '(' [ForStmt] ';' [Cond] ';' [ForStmt] ')' Stmt // h
    // 语句 ForStmt → LVal '=' Exp { ',' LVal '=' Exp } // h
    public void visitForStmt(ForStmtNode node) {
        if (node.getInitStmt() != null) {
            visitForSubStmt(node.getInitStmt());
        }
        if (node.getCond() != null) {
            hub.getExprVisitor().visitCond(node.getCond());
        }
        if (node.getUpdateStmt() != null) {
            visitForSubStmt(node.getUpdateStmt());
        }

        hub.incrementLoopDepth();
        visitStmt(node.getBodyStmt());
        hub.decrementLoopDepth();
    }

    // 语句 ForStmt → LVal '=' Exp { ',' LVal '=' Exp } // h
    public void visitForSubStmt(ForSubStmtNode node) {
        if (node == null) return;
        for (int i = 0; i < node.getLVals().size(); i++) {
            LValAnalysis lvalInfo = hub.getExprVisitor().visitLVal(node.getLVals().get(i));

            if (lvalInfo.symbol() instanceof ValueSymbol) {
                ValueSymbol vs = (ValueSymbol) lvalInfo.symbol();
                if (vs.isConst()) {
                    ErrorReporter.addError(node.getLVals().get(i).getLineNumber(), 'h');
                }
            }
            hub.getExprVisitor().visitExp(node.getExps().get(i));
        }
    }

    // // | 'if' '(' Cond ')' Stmt [ 'else' Stmt ]
    public void visitIfStmt(IfStmtNode node) {
        hub.getExprVisitor().visitCond(node.getCond());
        visitStmt(node.getThenStmt());
        if (node.getElseStmt() != null) {
            visitStmt(node.getElseStmt());
        }
    }

    // | 'break' ';' | 'continue' ';' // m
    public void visitBreakStmt(BreakStmtNode node) {
        if (hub.getLoopDepth() == 0) {
            ErrorReporter.addError(node.getLineNumber(), 'm');
        }
    }

    // | 'break' ';' | 'continue' ';' // m
    public void visitContinueStmt(ContinueStmtNode node) {
        if (hub.getLoopDepth() == 0) {
            ErrorReporter.addError(node.getLineNumber(), 'm');
        }
    }

    // | 'return' [Exp] ';' // f
    public void visitReturnStmt(ReturnStmtNode node) {
        FuncSymbol func = hub.getCurrentFunction();
        if (func == null) {
            return;
        }

        boolean hasExp = (node.getExp() != null);
        boolean expectsExp = (func.getReturnType() == SymbolType.IntFunc);

        // void函数return了exp
        if (hasExp && !expectsExp) {
            ErrorReporter.addError(node.getLineNumber(), 'f');
        }
        if (hasExp) {
            SymbolType returnType = hub.getExprVisitor().visitExp(node.getExp());
            // (未来任务：检查 returnType 是否为 Int)
        }
    }

    // | 'printf''('StringConst {','Exp}')'';' // l
    public void visitPrintfStmt(PrintfStmtNode node) {
        String formatString = (String) node.getFormatString().getValue();
        int expectedCount = 0;

        // 注意：这没有处理 %%d, %%, %d%d 等边缘情况
        int index = formatString.indexOf("%d");
        while (index != -1) {
            expectedCount++;
            index = formatString.indexOf("%d", index + 2);
        }
        int actualCount = node.getExps().size();
        if (expectedCount != actualCount) {
            ErrorReporter.addError(node.getLineNumber(), 'l');
        }

        for (ExpNode exp : node.getExps()) {
            SymbolType paramType = hub.getExprVisitor().visitExp(exp);
            // (未来任务：检查 paramType 是否为 Int)
        }
    }
}