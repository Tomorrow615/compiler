package io.github.tomorrow615.compiler.frontend.visitor;

import io.github.tomorrow615.compiler.frontend.ast.*;
import io.github.tomorrow615.compiler.frontend.ast.decl.*;
import io.github.tomorrow615.compiler.frontend.ast.func.*;
import io.github.tomorrow615.compiler.frontend.ast.stmt.*;
import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.error.ErrorReporter;
import io.github.tomorrow615.compiler.frontend.lexer.*;
import io.github.tomorrow615.compiler.frontend.symbol.*;

import java.util.List;

public class ExpressionVisitor {
    private final SemanticVisitor hub;

    public ExpressionVisitor(SemanticVisitor hub) {
        this.hub = hub;
    }

    public Symbol visitLVal_for_Symbol(LValNode node) {
        if (node == null) return null;
        Token ident = node.getIdent();
        Symbol symbol = hub.getCurrentScope().lookup(ident.getText());

        if (symbol == null) {
            // 错误 c: 未定义的名字
            ErrorReporter.addError(ident.getLineNumber(), 'c');
            return null;
        }
        return symbol;
    }

    public SymbolType visitLVal_for_Type(LValNode node) {
        if (node == null) return null;
        Token ident = node.getIdent();
        Symbol symbol = hub.getCurrentScope().lookup(ident.getText());

        if (symbol == null) {
            ErrorReporter.addError(ident.getLineNumber(), 'c');
            return null; // 类型错误
        }

        if (!(symbol instanceof ValueSymbol vs)) {
            // 错误 c: 试图将函数名当作变量使用
            ErrorReporter.addError(ident.getLineNumber(), 'c');
            return null; // 类型错误
        }

        // --- (P3 新增) LVal 维度检查 ---
        int symbolDim = vs.getDimension();
        int usageDim = node.getArrayExps().size();

        // 访问所有索引表达式 (e.g., a[i+j])
        for (ExpNode indexExp : node.getArrayExps()) {
            SymbolType indexType = visitExp(indexExp);
            // (未来任务：检查 indexType 是否为 Int)
        }

        if (symbolDim == 0 && usageDim > 0) {
            // 错误 (e类): 对非数组变量使用 '[]' (e.g., int a; a[0]=1;)
            ErrorReporter.addError(ident.getLineNumber(), 'e');
            return null;
        } else if (symbolDim > 0 && usageDim == 0) {
            // 用法：int a[10]; ... a ... (返回数组类型)
            return vs.getType(); // e.g., IntArray
        } else if (symbolDim == 1 && usageDim == 1) { // SysY 只有一维数组
            // 用法：int a[10]; ... a[0] ... (返回元素类型)
            return SymbolType.Int;
        } else if (symbolDim != usageDim) {
            // 错误 (e类): 维度不匹配
            ErrorReporter.addError(ident.getLineNumber(), 'e');
            return null;
        }

        // 默认情况： int a; ... a ... (symbolDim == 0 && usageDim == 0)
        return vs.getType(); // e.g., Int
    }

    // --- (P3 修改) 表达式递归访问链 (返回 SymbolType) ---

    public SymbolType visitExp(ExpNode node) {
        if (node == null) return null;
        // 表达式的类型 = AddExp 的类型
        return visitAddExp(node.getAddExp());
    }

    public SymbolType visitCond(CondNode node) {
        if (node == null) return null;
        // (P3 新增) 访问 LOrExp
        return visitLOrExp(node.getLorExp());
    }

    public SymbolType visitLOrExp(LOrExpNode node) {
        if (node == null) return null;

        // 访问第一个子节点
        SymbolType firstType = visitLAndExp(node.getlAndExps().get(0));

        if (node.getOperators().isEmpty()) {
            // 没有运算符 (如 '||')，这是一个"直通"节点
            // 返回子节点的真实类型
            return firstType;
        } else {
            // 有运算符，访问其他子节点
            for (int i = 1; i < node.getlAndExps().size(); i++) {
                visitLAndExp(node.getlAndExps().get(i));
            }
            // (P3 简化) 逻辑运算的结果视为 Int (1 或 0)
            return SymbolType.Int;
        }
    }

    public SymbolType visitLAndExp(LAndExpNode node) {
        if (node == null) return null;

        // 访问第一个子节点
        SymbolType firstType = visitEqExp(node.getEqExps().get(0));

        if (node.getOperators().isEmpty()) {
            // 没有运算符 (如 '&&')，这是一个"直通"节点
            // 返回子节点的真实类型
            return firstType;
        } else {
            // 有运算符，访问其他子节点
            for (int i = 1; i < node.getEqExps().size(); i++) {
                visitEqExp(node.getEqExps().get(i));
            }
            // 逻辑运算的结果视为 Int
            return SymbolType.Int;
        }
    }

    public SymbolType visitEqExp(EqExpNode node) {
        if (node == null) return null;

        // 访问第一个子节点
        SymbolType firstType = visitRelExp(node.getRelExps().get(0));

        if (node.getOperators().isEmpty()) {
            // 没有运算符 (如 '==')，这是一个"直通"节点
            // 返回子节点的真实类型
            return firstType;
        } else {
            // 有运算符，访问其他子节点
            for (int i = 1; i < node.getRelExps().size(); i++) {
                visitRelExp(node.getRelExps().get(i));
            }
            // 相等性运算的结果视为 Int
            return SymbolType.Int;
        }
    }

    public SymbolType visitRelExp(RelExpNode node) {
        if (node == null) return null;

        // 访问第一个子节点
        SymbolType firstType = visitAddExp(node.getAddExps().get(0));

        if (node.getOperators().isEmpty()) {
            // 没有运算符 (如 '<')，这是一个"直通"节点
            // 返回子节点的真实类型
            return firstType;
        } else {
            // 有运算符，访问其他子节点
            for (int i = 1; i < node.getAddExps().size(); i++) {
                visitAddExp(node.getAddExps().get(i));
            }
            // 关系运算的结果视为 Int
            return SymbolType.Int;
        }
    }

    public SymbolType visitAddExp(AddExpNode node) {
        if (node == null) return null;

        // 访问第一个子节点
        SymbolType firstType = visitMulExp(node.getMulExps().get(0));

        if (node.getOperators().isEmpty()) {
            // 没有运算符 (如 '+')，这是一个"直通"节点
            // 返回子节点的真实类型
            return firstType;
        } else {
            // 有运算符，访问其他子节点
            for (int i = 1; i < node.getMulExps().size(); i++) {
                visitMulExp(node.getMulExps().get(i));
                // (P3 任务: 检查 firstType 和其他类型是否为 Int)
            }
            // 算术运算的结果视为 Int
            return SymbolType.Int;
        }
    }

    public SymbolType visitMulExp(MulExpNode node) {
        if (node == null) return null;

        // 访问第一个子节点
        SymbolType firstType = visitUnaryExp(node.getUnaryExps().get(0));

        if (node.getOperators().isEmpty()) {
            // 没有运算符 (如 '*')，这是一个"直通"节点
            // 返回子节点的真实类型
            return firstType;
        } else {
            // 有运算符，访问其他子节点
            for (int i = 1; i < node.getUnaryExps().size(); i++) {
                visitUnaryExp(node.getUnaryExps().get(i));
                // (P3 任务: 检查 firstType 和其他类型是否为 Int)
            }
            // 算术运算的结果视为 Int
            return SymbolType.Int;
        }
    }

    /**
     * (P3 修改) 访问一元表达式 (UnaryExpNode)。
     * 返回推导出的类型。
     */
    public SymbolType visitUnaryExp(UnaryExpNode node) {
        if (node == null) return null;

        switch (node.getType()) {
            case PRIMARY:
                return visitPrimaryExp(node.getPrimaryExp());
            case UNARY_OP:
                visitUnaryExp(node.getUnaryExp()); // 递归访问
                return SymbolType.Int; // 单目运算结果为 Int
            case FUNC_CALL:
                Token ident = node.getIdent();
                Symbol symbol = hub.getCurrentScope().lookup(ident.getText());

                if (symbol == null) {
                    ErrorReporter.addError(ident.getLineNumber(), 'c');
                    return null;
                }

                if (!(symbol instanceof FuncSymbol fs)) {
                    ErrorReporter.addError(ident.getLineNumber(), 'c');
                    return null;
                }

                int expectedCount = fs.getParameters().size();
                int actualCount = (node.getFuncRParams() == null) ? 0 : node.getFuncRParams().getParams().size();

                if (expectedCount != actualCount) {
                    ErrorReporter.addError(ident.getLineNumber(), 'd');
                }

                // --- (P3 新增) 错误 'e' (函数参数类型不匹配) ---
                if (node.getFuncRParams() != null && expectedCount == actualCount) {
                    List<ValueSymbol> expectedParams = fs.getParameters();
                    List<ExpNode> actualParams = node.getFuncRParams().getParams();

                    for (int i = 0; i < expectedCount; i++) {
                        SymbolType expectedType = expectedParams.get(i).getType(); // e.g., Int 或 IntArray
                        SymbolType actualType = visitExp(actualParams.get(i)); // 递归调用，获取实参类型

                        // 检查类型是否匹配
                        // --- 修改开始 ---
                        if (actualType != null && !areTypesCompatible(expectedType, actualType)) {
                            // --- 修改结束 ---
                            // (e.g., 期望 Int, 得到了 IntArray, 或者反之)
                            ErrorReporter.addError(node.getIdent().getLineNumber(), 'e');
                        }
                    }
                }

                // 返回函数的返回类型 (IntFunc -> Int, VoidFunc -> Void)
                if (fs.getReturnType() == SymbolType.IntFunc) {
                    return SymbolType.Int;
                } else {
                    return SymbolType.VoidFunc; // (P3 任务：需要一种 Void 类型)
                }
        }
        return null;
    }

    /**
     * (P3 修改) 访问基本表达式 (PrimaryExpNode)。
     * 返回推导出的类型。
     */
    public SymbolType visitPrimaryExp(PrimaryExpNode node) {
        if (node == null) return null;

        switch (node.getType()) {
            case LVAL:
                return visitLVal_for_Type(node.getLval()); // (P3 修改)
            case PAREN_EXP:
                return visitExp(node.getExp());
            case NUMBER:
                return SymbolType.Int; // Number 总是 Int
        }
        return null;
    }

    /**
     * 检查函数参数类型是否兼容。
     * e.g., ConstInt 和 StaticInt 都可以兼容 Int。
     * ConstIntArray 和 StaticIntArray 都可以兼容 IntArray。
     */
    // 位于 ExpressionVisitor.java
    private boolean areTypesCompatible(SymbolType expected, SymbolType actual) {
        if (expected == null || actual == null) {
            return false;
        }

        // 规则 1: 期望 Int (标量)
        if (expected == SymbolType.Int) {
            // 普通常量 (ConstInt) 和 静态变量 (StaticInt) 都可以传递
            return actual == SymbolType.Int ||
                    actual == SymbolType.ConstInt ||
                    actual == SymbolType.StaticInt;
        }

        // 规则 2: 期望 IntArray (数组)
        if (expected == SymbolType.IntArray) {
            // 根据规范 ，普通常量数组 (ConstIntArray) *不可以* 传递
            // 静态数组 (StaticIntArray) 可以传递
            return actual == SymbolType.IntArray ||
                    actual == SymbolType.StaticIntArray;
        }

        // 默认规则：对于 VoidFunc 等其他类型，使用严格相等
        return expected.equals(actual);
    }
}