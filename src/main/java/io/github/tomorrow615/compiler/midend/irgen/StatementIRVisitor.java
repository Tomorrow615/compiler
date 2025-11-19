package io.github.tomorrow615.compiler.midend.irgen;

import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.ast.stmt.*;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.List;

public class StatementIRVisitor {

    private final IRGeneratorVisitor hub;
    private final IRBuilder builder;
    private final ExpressionIRVisitor exprVisitor;

    public StatementIRVisitor(IRGeneratorVisitor hub, IRBuilder builder, ExpressionIRVisitor exprVisitor) {
        this.hub = hub;
        this.builder = builder;
        this.exprVisitor = exprVisitor;
    }

    // ==========================================
    //          通用入口 (Dispatcher)
    // ==========================================

    public void visitStmt(StmtNode node) {
        if (node == null) return;

        if (node instanceof AssignStmtNode a) {
            visitAssignStmt(a);
        } else if (node instanceof ExpStmtNode e) {
            visitExpStmt(e);
        } else if (node instanceof ReturnStmtNode r) {
            visitReturnStmt(r);
        } else if (node instanceof PrintfStmtNode p) {
            visitPrintfStmt(p);
        } else if (node instanceof IfStmtNode i) {
            visitIfStmt(i);
        } else if (node instanceof ForStmtNode f) {
            visitForStmt(f);
        } else if (node instanceof BreakStmtNode b) {
            visitBreakStmt(b);
        } else if (node instanceof ContinueStmtNode c) {
            visitContinueStmt(c);
        } else if (node instanceof BlockNode b) {
            // Block 需要特殊处理作用域，委托给 Hub
            hub.enterScope();
            hub.visitBlock(b); // Hub 那边也会有一个 explicit 的 visitBlock
            hub.exitScope();
        }
    }

    // ==========================================
    //          具体语句处理
    // ==========================================

    public void visitAssignStmt(AssignStmtNode node) {
        // 1. 计算右侧 Exp 的值 (使用通用入口 visitExpression)
        Value valToStore = exprVisitor.visitExpression(node.getExp());

        // 2. 获取左侧 LVal 的地址指针 (使用明确的 buildLValPointer)
        Value ptr = exprVisitor.buildLValPointer(node.getlVal());

        // 3. 创建 store 指令
        if (valToStore != null && ptr != null) {
            builder.createStore(valToStore, ptr);
        }
    }

    public void visitExpStmt(ExpStmtNode node) {
        if (node.getExp() != null) {
            // 只计算值，不使用 (用于副作用，如函数调用)
            exprVisitor.visitExpression(node.getExp());
        }
    }

    public void visitReturnStmt(ReturnStmtNode node) {
        if (node.getExp() != null) {
            // 有返回值 'return <exp>;'
            Value retVal = exprVisitor.visitExpression(node.getExp());
            builder.createRet(retVal);
        } else {
            // 'return;' (void)
            builder.createRetVoid();
        }
    }

    public void visitPrintfStmt(PrintfStmtNode node) {
        Token formatStringToken = node.getFormatString();
        String formatString = (String) formatStringToken.getValue();
        List<ExpNode> exps = node.getExps();

        int expIndex = 0;
        Function putintFunc = hub.getIoFunctions().get("putint");
        Function putstrFunc = hub.getIoFunctions().get("putstr");

        StringBuilder strFragment = new StringBuilder();

        for (int i = 0; i < formatString.length(); i++) {
            if (formatString.charAt(i) == '%') {
                if (i + 1 < formatString.length() && formatString.charAt(i + 1) == 'd') {
                    // 1. 遇到 %d，先把前面收集的字符串打印出去
                    if (strFragment.length() > 0) {
                        printStringFragment(strFragment.toString(), putstrFunc);
                        strFragment.setLength(0);
                    }

                    // 2. 打印 %d 对应的表达式
                    if (expIndex < exps.size()) {
                        Value val = exprVisitor.visitExpression(exps.get(expIndex++));
                        builder.createCall(putintFunc, List.of(val), "");
                    }
                    i++; // 跳过 'd'
                } else {
                    strFragment.append(formatString.charAt(i));
                }
            } else {
                strFragment.append(formatString.charAt(i));
            }
        }

        if (strFragment.length() > 0) {
            printStringFragment(strFragment.toString(), putstrFunc);
        }
    }

    private void printStringFragment(String text, Function putstrFunc) {
        GlobalVariable gv = hub.getGlobalString(text);
        Value zero = new ConstantInt(0);
        Value ptr = builder.createGep(gv, List.of(zero, zero), "str.ptr");
        builder.createCall(putstrFunc, List.of(ptr), "");
    }

    public void visitIfStmt(IfStmtNode node) {
        Function currentFunction = hub.getCurrentFunction();

        BasicBlock thenBB = new BasicBlock(hub.getNextIfThenLabel(), currentFunction);
        BasicBlock mergeBB = new BasicBlock(hub.getNextIfMergeLabel(), currentFunction);
        BasicBlock elseBB = (node.getElseStmt() != null) ?
                new BasicBlock(hub.getNextIfElseLabel(), currentFunction) : null;

        // --- 使用新的显式 API 生成条件跳转 ---
        if (elseBB != null) {
            // If-Else
            exprVisitor.buildConditionBranch(node.getCond(), thenBB, elseBB);
        } else {
            // If-Only
            exprVisitor.buildConditionBranch(node.getCond(), thenBB, mergeBB);
        }

        // 7. 填充 then 块
        builder.setInsertPoint(thenBB);
        visitStmt(node.getThenStmt()); // 递归调用分发器
        if (!builder.getCurrentBlock().hasTerminator()) {
            builder.createBr(mergeBB);
        }

        // 8. 填充 else 块
        if (elseBB != null) {
            builder.setInsertPoint(elseBB);
            visitStmt(node.getElseStmt()); // 递归调用分发器
            if (!builder.getCurrentBlock().hasTerminator()) {
                builder.createBr(mergeBB);
            }
        }

        // 9. 设置插入点为 merge 块
        builder.setInsertPoint(mergeBB);
    }

    public void visitForStmt(ForStmtNode node) {
        Function currentFunction = hub.getCurrentFunction();

        BasicBlock condBB = new BasicBlock("for.cond", currentFunction);
        BasicBlock bodyBB = new BasicBlock("for.body", currentFunction);
        BasicBlock updateBB = new BasicBlock("for.update", currentFunction);
        BasicBlock mergeBB = new BasicBlock("for.merge", currentFunction);

        hub.pushLoop(mergeBB, updateBB);

        // [Init]
        if (node.getInitStmt() != null) {
            ForSubStmtNode init = node.getInitStmt();
            for (int i = 0; i < init.getLVals().size(); i++) {
                Value val = exprVisitor.visitExpression(init.getExps().get(i));
                Value ptr = exprVisitor.buildLValPointer(init.getLVals().get(i)); // 明确获取指针
                builder.createStore(val, ptr);
            }
        }
        builder.createBr(condBB);

        // [Cond]
        builder.setInsertPoint(condBB);
        if (node.getCond() != null) {
            // --- 使用新的显式 API 生成条件跳转 ---
            exprVisitor.buildConditionBranch(node.getCond(), bodyBB, mergeBB);
        } else {
            builder.createBr(bodyBB);
        }

        // [Body]
        builder.setInsertPoint(bodyBB);
        visitStmt(node.getBodyStmt()); // 递归调用分发器

        if (!builder.getCurrentBlock().hasTerminator()) {
            builder.createBr(updateBB);
        }

        // [Update]
        builder.setInsertPoint(updateBB);
        if (node.getUpdateStmt() != null) {
            ForSubStmtNode update = node.getUpdateStmt();
            for (int i = 0; i < update.getLVals().size(); i++) {
                Value val = exprVisitor.visitExpression(update.getExps().get(i));
                Value ptr = exprVisitor.buildLValPointer(update.getLVals().get(i)); // 明确获取指针
                builder.createStore(val, ptr);
            }
        }
        builder.createBr(condBB);

        // [Merge]
        builder.setInsertPoint(mergeBB);
        hub.popLoop();
    }

    public void visitBreakStmt(BreakStmtNode node) {
        builder.createBr(hub.getCurrentLoopMergeBB());
    }

    public void visitContinueStmt(ContinueStmtNode node) {
        builder.createBr(hub.getCurrentLoopUpdateBB());
    }
}