// 完整路径: E:\compiler\src\main\java\io\github\tomorrow615\compiler\midend\StatementIRVisitor.java
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

import java.util.ArrayList;
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

    public void visit(StmtNode node) {
        if (node == null) return;

        // --- [ 修改开始：删除 BlockNode 的 if-else ] ---
        if (node instanceof AssignStmtNode a) {
            visit(a);
        } else if (node instanceof ExpStmtNode e) {
            visit(e);
        } else if (node instanceof ReturnStmtNode r) {
            visit(r);
        } else if (node instanceof PrintfStmtNode p) {
            visit(p);
        }
        // (BlockNode 检查已被移除)
        // --- [ 修改结束 ] ---
    }

    private void visit(AssignStmtNode node) {
        // 1. 计算右侧 Exp 的值
        Value valToStore = exprVisitor.visit(node.getExp());

        // 2. 获取左侧 LVal 的指针
        Value ptr = exprVisitor.getPointerToLVal(node.getlVal());

        // 3. 创建 store 指令
        if (valToStore != null && ptr != null) {
            builder.createStore(valToStore, ptr);
        }
    }

    private void visit(ExpStmtNode node) {
        if (node.getExp() != null) {
            // 只 visit，不使用返回值 (用于执行函数调用并丢弃结果)
            exprVisitor.visit(node.getExp());
        }
    }

    private void visit(ReturnStmtNode node) {
        if (node.getExp() != null) {
            // 有返回值 'return <exp>;'
            Value retVal = exprVisitor.visit(node.getExp());
            builder.createRet(retVal);
        } else {
            // 'return;' (void)
            builder.createRetVoid();
        }
    }


    private void visit(PrintfStmtNode node) {
        Token formatStringToken = node.getFormatString();
        String formatString = (String) formatStringToken.getValue();
        List<ExpNode> exps = node.getExps();

        int expIndex = 0;
        Function putintFunc = hub.getIoFunctions().get("putint");
        Function putstrFunc = hub.getIoFunctions().get("putstr");

        // 用于收集连续的普通字符串
        StringBuilder strFragment = new StringBuilder();

        for (int i = 0; i < formatString.length(); i++) {
            if (formatString.charAt(i) == '%') {
                if (i + 1 < formatString.length() && formatString.charAt(i + 1) == 'd') {
                    // 1. 遇到 %d，先把前面收集的字符串打印出去
                    if (strFragment.length() > 0) {
                        printStringFragment(strFragment.toString(), putstrFunc);
                        strFragment.setLength(0); // 清空
                    }

                    // 2. 打印 %d 对应的表达式
                    if (expIndex < exps.size()) {
                        Value val = exprVisitor.visit(exps.get(expIndex++));
                        builder.createCall(putintFunc, List.of(val), "");
                    }
                    i++; // 跳过 'd'
                } else {
                    // 无法识别的 % (例如 %% 或 %a)，当作普通字符
                    strFragment.append(formatString.charAt(i));
                }
            } else {
                // 普通字符，加入片段
                strFragment.append(formatString.charAt(i));
            }
        }

        // 打印循环结束后剩余的最后一段字符串
        if (strFragment.length() > 0) {
            printStringFragment(strFragment.toString(), putstrFunc);
        }
    }

    private void printStringFragment(String text, Function putstrFunc) {
        // 1. 从 Hub 获取或创建全局字符串常量
        GlobalVariable gv = hub.getGlobalString(text);

        // 2. 创建 GEP 指令获取 i8*
        // GEP @.str, i32 0, i32 0
        Value zero = new ConstantInt(0);
        Value ptr = builder.createGep(gv, List.of(zero, zero), "str.ptr");

        // 3. 调用 putstr
        builder.createCall(putstrFunc, List.of(ptr), "");
    }
}