package io.github.tomorrow615.compiler.frontend.visitor;

import io.github.tomorrow615.compiler.frontend.ast.*;
import io.github.tomorrow615.compiler.frontend.ast.decl.*;
import io.github.tomorrow615.compiler.frontend.ast.func.*;
import io.github.tomorrow615.compiler.frontend.ast.stmt.*;
import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.error.ErrorReporter;
import io.github.tomorrow615.compiler.frontend.lexer.*;
import io.github.tomorrow615.compiler.frontend.symbol.*;

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

    public void visitStmt(StmtNode node) {
        if (node == null) return;

        if (node instanceof BlockNode b) {
            // 这是一个 *嵌套* 的语句块, e.g., if (...) { ... }
            // 它需要创建自己的作用域
            hub.enterScope();
            visitBlock(b); // 遍历块的内容
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

    public void visitAssignStmt(AssignStmtNode node) {
        // 1. (P3 修改) 访问 LVal 以获取 *Symbol* (用于 'h' 检查)
        Symbol symbol = hub.getExprVisitor().visitLVal_for_Symbol(node.getlVal());

        // 2. 检查错误 'h'
        if (symbol != null && symbol instanceof ValueSymbol vs && vs.isConst()) {
            ErrorReporter.addError(node.getLineNumber(), 'h');
        }

        // 3. (P3 修改) 递归访问 node.getExp() 以推导其类型并检查内部错误
        if (node.getExp() != null) {
            // (P3 任务：获取类型并与 LVal 比较)
            SymbolType lvalType = hub.getExprVisitor().visitLVal_for_Type(node.getlVal());
            SymbolType expType = hub.getExprVisitor().visitExp(node.getExp());

            // (未来任务：检查 lvalType 和 expType 是否匹配)
        }
    }

    public void visitExpStmt(ExpStmtNode node) {
        if (node.getExp() != null) {
            hub.getExprVisitor().visitExp(node.getExp());
        }
    }

    public void visitForStmt(ForStmtNode node) {
        if (node.getInitStmt() != null) {
            visitForSubStmt(node.getInitStmt());
        }
        if (node.getCond() != null) {
            // 委托给 ExpressionVisitor 访问条件
            hub.getExprVisitor().visitCond(node.getCond());
        }
        if (node.getUpdateStmt() != null) {
            visitForSubStmt(node.getUpdateStmt());
        }

        // 关键：管理循环层级
        hub.incrementLoopDepth();
        visitStmt(node.getBodyStmt()); // 递归访问循环体
        hub.decrementLoopDepth();
    }

    public void visitForSubStmt(ForSubStmtNode node) {
        if (node == null) return;

        for (int i = 0; i < node.getLVals().size(); i++) {
            // 1. (P3 修改) 访问 LVal 以获取 *Symbol* (用于 'h' 检查)
            Symbol symbol = hub.getExprVisitor().visitLVal_for_Symbol(node.getLVals().get(i));

            // 2. 检查 'h' (常量赋值)
            if (symbol != null && symbol instanceof ValueSymbol vs && vs.isConst()) {
                ErrorReporter.addError(node.getLVals().get(i).getLineNumber(), 'h');
            }

            // 3. (P3 修改) 访问 Exp (推导类型并检查内部错误)
            hub.getExprVisitor().visitExp(node.getExps().get(i));
        }
    }

    public void visitIfStmt(IfStmtNode node) {
        // 1. 访问条件 (这会递归触发 ExprVisitor 检查 'c', 'd' 等)
        hub.getExprVisitor().visitCond(node.getCond());

        // 2. 访问 then 语句
        visitStmt(node.getThenStmt());

        // 3. 访问 else 语句 (如果存在)
        if (node.getElseStmt() != null) {
            visitStmt(node.getElseStmt());
        }
    }

    public void visitBreakStmt(BreakStmtNode node) {
        if (hub.getLoopDepth() == 0) {
            // 错误 m: 在非循环块中使用 break
            ErrorReporter.addError(node.getLineNumber(), 'm');
        }
    }

    public void visitContinueStmt(ContinueStmtNode node) {
        if (hub.getLoopDepth() == 0) {
            // 错误 m: 在非循环块中使用 continue
            ErrorReporter.addError(node.getLineNumber(), 'm');
        }
    }

    public void visitReturnStmt(ReturnStmtNode node) {
        FuncSymbol func = hub.getCurrentFunction();
        if (func == null) {
            return; // 理论上不应发生 (除非全局 return)
        }

        boolean hasExp = (node.getExp() != null);
        boolean expectsExp = (func.getReturnType() == SymbolType.IntFunc);

        // 错误 f: 无返回值的函数存在不匹配的 return 语句
        if (hasExp && !expectsExp) {
            // void func() { return 1; }
            ErrorReporter.addError(node.getLineNumber(), 'f');
        } else if (!hasExp && expectsExp) {
            // int func() { return; }
            // (SysY 规范中，int func() { return; } 也是错误 f)
            ErrorReporter.addError(node.getLineNumber(), 'f');
        }

        // 访问 return 后的表达式 (如果有)
        if (hasExp) {
            SymbolType returnType = hub.getExprVisitor().visitExp(node.getExp());
            // (未来任务：检查 returnType 是否为 Int)
        }
    }

    public void visitPrintfStmt(PrintfStmtNode node) {
        // 1. 检查错误 'l' (printf中格式字符与表达式个数不匹配)
        // 注意：.getValue() 返回的是 *不带* 双引号的字符串值
        String formatString = (String) node.getFormatString().getValue();
        int expectedCount = 0;

        // 简单地计算 %d 的数量
        // (注意：这没有处理 %%d, %%, %d%d 等边缘情况，但对于 SysY 足够了)
        int index = formatString.indexOf("%d");
        while (index != -1) {
            expectedCount++;
            index = formatString.indexOf("%d", index + 2);
        }

        int actualCount = node.getExps().size();

        if (expectedCount != actualCount) {
            ErrorReporter.addError(node.getLineNumber(), 'l');
        }

        // 2. 递归访问所有参数表达式，检查其中的错误 (如 'c', 'd')
        for (ExpNode exp : node.getExps()) {
            SymbolType paramType = hub.getExprVisitor().visitExp(exp);
            // (未来任务：检查 paramType 是否为 Int)
        }
    }
}