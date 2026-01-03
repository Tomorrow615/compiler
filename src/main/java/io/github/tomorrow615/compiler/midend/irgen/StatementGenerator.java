package io.github.tomorrow615.compiler.midend.irgen;

import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.ast.stmt.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.List;
import java.util.ArrayList;

public class StatementGenerator {
    private final IRGenContext context;
    private final IRBuilder builder;
    private final ExpressionGenerator exprGen;
    private final IRGenerator mainGen;
    private BreakStmtNode node;

    public StatementGenerator(IRGenContext context, ExpressionGenerator exprGen, IRGenerator mainGen) {
        this.context = context;
        this.builder = context.getBuilder();
        this.exprGen = exprGen;
        this.mainGen = mainGen;
    }

    public void visitStmt(StmtNode node) {
        if (node == null) return;
        if (node instanceof AssignStmtNode a) visitAssignStmt(a);
        else if (node instanceof ExpStmtNode e) visitExpStmt(e);
        else if (node instanceof ReturnStmtNode r) visitReturnStmt(r);
        else if (node instanceof PrintfStmtNode p) visitPrintfStmt(p);
        else if (node instanceof IfStmtNode i) visitIfStmt(i);
        else if (node instanceof ForStmtNode f) visitForStmt(f);
        else if (node instanceof BreakStmtNode b) visitBreakStmt(b);
        else if (node instanceof ContinueStmtNode c) visitContinueStmt(c);
        else if (node instanceof BlockNode b) {
            context.enterScope();
            mainGen.visitBlock(b);
            context.exitScope();
        }
    }

    public void visitAssignStmt(AssignStmtNode node) {
        Value valToStore = exprGen.visitExpression(node.getExp());
        Value ptr = exprGen.buildLValPointer(node.getlVal());
        if (valToStore != null && ptr != null) {
            builder.createStore(valToStore, ptr);
        }
    }

    public void visitExpStmt(ExpStmtNode node) {
        if (node.getExp() != null) {
            exprGen.visitExpression(node.getExp());
        }
    }

    public void visitReturnStmt(ReturnStmtNode node) {
        if (node.getExp() != null) {
            Value retVal = exprGen.visitExpression(node.getExp());
            builder.createRet(retVal);
        } else {
            builder.createRetVoid();
        }
    }

    public void visitPrintfStmt(PrintfStmtNode node) {
        String formatString = (String) node.getFormatString().getValue();
        List<ExpNode> exps = node.getExps();
        List<Value> evaluatedValues = new ArrayList<>();
        for (ExpNode exp : exps) {
            evaluatedValues.add(exprGen.visitExpression(exp));
        }

        int expIndex = 0;
        Function putintFunc = context.getIoFunctions().get("putint");
        Function putstrFunc = context.getIoFunctions().get("putstr");
        StringBuilder strFragment = new StringBuilder();

        for (int i = 0; i < formatString.length(); i++) {
            if (formatString.charAt(i) == '%' && i + 1 < formatString.length() && formatString.charAt(i + 1) == 'd') {
                if (!strFragment.isEmpty()) {
                    printStringFragment(strFragment.toString(), putstrFunc);
                    strFragment.setLength(0);
                }
                if (expIndex < evaluatedValues.size()) {
                    builder.createCall(putintFunc, List.of(evaluatedValues.get(expIndex++)), "");
                }
                i++;
            } else {
                strFragment.append(formatString.charAt(i));
            }
        }
        if (!strFragment.isEmpty()) {
            printStringFragment(strFragment.toString(), putstrFunc);
        }
    }

    private void printStringFragment(String text, Function putstrFunc) {
        GlobalVariable gv = context.getGlobalString(text);
        Value zero = new ConstantInt(0);
        Value ptr = builder.createGep(gv, List.of(zero, zero), "str.ptr");
        builder.createCall(putstrFunc, List.of(ptr), "");
    }

    public void visitIfStmt(IfStmtNode node) {
        Function currentFunc = context.getCurrentFunction();
        BasicBlock thenBB = new BasicBlock(context.getNextLabel("if.then"), currentFunc);
        BasicBlock mergeBB = new BasicBlock(context.getNextLabel("if.merge"), currentFunc);
        BasicBlock elseBB = (node.getElseStmt() != null) ?
                new BasicBlock(context.getNextLabel("if.else"), currentFunc) : null;

        /* [原生支持方案] 处理 if (int a = 1) 语法
           如果 IfStmtNode 有 initDecl 字段，需要：
           1. 先进入作用域（变量 a 只在 if 内有效）
           2. 生成变量声明的 IR
           3. 再生成条件判断
           4. 最后退出作用域
        
        boolean hasInitDecl = (node.getInitDecl() != null);
        if (hasInitDecl) {
            context.enterScope(); // 开启 if-level 作用域
            mainGen.visitVarDecl(node.getInitDecl()); // 生成 alloca + store
        }
        */

        exprGen.buildConditionBranch(node.getCond(), thenBB, elseBB != null ? elseBB : mergeBB);

        builder.setInsertPoint(thenBB);
        visitStmt(node.getThenStmt());
        if (!builder.getCurrentBlock().hasTerminator()) builder.createBr(mergeBB);

        if (elseBB != null) {
            builder.setInsertPoint(elseBB);
            visitStmt(node.getElseStmt());
            if (!builder.getCurrentBlock().hasTerminator()) builder.createBr(mergeBB);
        }
        builder.setInsertPoint(mergeBB);

        /* [原生支持方案] 退出作用域
        if (hasInitDecl) {
            context.exitScope();
        }
        */
    }

    // [NEW4] ForStmt → BType Ident '=' InitVal (for循环内声明变量)
    public void visitForStmt(ForStmtNode node) {
        /* [NEW4] 处理 for(int i = 1;;) 语法
        boolean hasInitDecl = (node.getInitDecl() != null);
        if (hasInitDecl) {
            context.enterScope(); // 开启 for-level 作用域
            mainGen.visitVarDecl(node.getInitDecl()); // 生成 alloca + store
        }
        */
        
        Function currentFunc = context.getCurrentFunction();
        BasicBlock condBB = new BasicBlock(context.getNextLabel("for.cond"), currentFunc);
        BasicBlock bodyBB = new BasicBlock(context.getNextLabel("for.body"), currentFunc);
        BasicBlock updateBB = new BasicBlock(context.getNextLabel("for.update"), currentFunc);
        BasicBlock mergeBB = new BasicBlock(context.getNextLabel("for.merge"), currentFunc);

        context.pushLoop(mergeBB, updateBB);

        if (node.getInitStmt() != null) {
            ForSubStmtNode init = node.getInitStmt();
            for (int i = 0; i < init.getLVals().size(); i++) {
                Value val = exprGen.visitExpression(init.getExps().get(i));
                Value ptr = exprGen.buildLValPointer(init.getLVals().get(i));
                builder.createStore(val, ptr);
            }
        }
        builder.createBr(condBB);

        builder.setInsertPoint(condBB);
        if (node.getCond() != null) {
            exprGen.buildConditionBranch(node.getCond(), bodyBB, mergeBB);
        } else {
            builder.createBr(bodyBB);
        }

        builder.setInsertPoint(bodyBB);
        visitStmt(node.getBodyStmt());
        if (!builder.getCurrentBlock().hasTerminator()) builder.createBr(updateBB);

        builder.setInsertPoint(updateBB);
        if (node.getUpdateStmt() != null) {
            ForSubStmtNode update = node.getUpdateStmt();
            for (int i = 0; i < update.getLVals().size(); i++) {
                Value val = exprGen.visitExpression(update.getExps().get(i));
                Value ptr = exprGen.buildLValPointer(update.getLVals().get(i));
                builder.createStore(val, ptr);
            }
        }
        builder.createBr(condBB);

        builder.setInsertPoint(mergeBB);
        context.popLoop();
        
        /* [NEW4] 退出作用域
        if (hasInitDecl) {
            context.exitScope();
        }
        */
    }

    public void visitBreakStmt(BreakStmtNode node) {
        this.node = node;
        builder.createBr(context.getCurrentLoopMergeBB());
    }

    public void visitContinueStmt(ContinueStmtNode node) {
        builder.createBr(context.getCurrentLoopUpdateBB());
    }
}