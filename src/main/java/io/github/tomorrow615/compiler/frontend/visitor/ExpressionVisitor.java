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

import java.util.List;

public class ExpressionVisitor {
    private final SemanticVisitor hub;

    public ExpressionVisitor(SemanticVisitor hub) {
        this.hub = hub;
    }

    // 左值表达式 LVal → Ident ['[' Exp ']'] // c
    public LValAnalysis visitLVal(LValNode node) {
        if (node == null) {
            return new LValAnalysis(null, null);
        }
        Symbol symbol = hub.getCurrentScope().lookup(node.getIdent().getText());

        if (!(symbol instanceof ValueSymbol valueSymbol)) {
            ErrorReporter.addError(node.getLineNumber(), 'c');
            return new LValAnalysis(null, null);
        }

        int actualDim = node.getArrayExps().size();
        int expectedDim = valueSymbol.getDimension();
        SymbolType resultingType;

        if (actualDim > expectedDim) {
            ErrorReporter.addError(node.getLineNumber(), 'c');
            resultingType = SymbolType.Int;
        } else if (actualDim == expectedDim) {
            resultingType = SymbolType.Int;
        } else {
            resultingType = SymbolType.IntArray;
        }

        for (ExpNode indexExp : node.getArrayExps()) {
            visitExp(indexExp);
        }

        return new LValAnalysis(symbol, resultingType);
    }

    // 表达式 Exp → AddExp
    public SymbolType visitExp(ExpNode node) {
        if (node == null) return null;
        return visitAddExp(node.getAddExp());
    }

    // 条件表达式 Cond → LOrExp
    public SymbolType visitCond(CondNode node) {
        if (node == null) return null;
        return visitLOrExp(node.getLorExp());
    }

    // 逻辑或表达式 LOrExp → LAndExp | LOrExp '||' LAndExp
    public SymbolType visitLOrExp(LOrExpNode node) {
        if (node == null) return null;
        SymbolType firstType = visitLAndExp(node.getlAndExps().get(0));
        if (node.getOperators().isEmpty()) {
            return firstType;
        } else {
            for (int i = 1; i < node.getlAndExps().size(); i++) {
                visitLAndExp(node.getlAndExps().get(i));
            }
            return SymbolType.Int;
        }
    }

    // 逻辑与表达式 LAndExp → EqExp | LAndExp '&&' EqExp
    public SymbolType visitLAndExp(LAndExpNode node) {
        if (node == null) return null;
        SymbolType firstType = visitEqExp(node.getEqExps().get(0));
        if (node.getOperators().isEmpty()) {
            return firstType;
        } else {
            for (int i = 1; i < node.getEqExps().size(); i++) {
                visitEqExp(node.getEqExps().get(i));
            }
            return SymbolType.Int;
        }
    }

    // 相等性表达式 EqExp → RelExp | EqExp ('==' | '!=') RelExp
    public SymbolType visitEqExp(EqExpNode node) {
        if (node == null) return null;
        SymbolType firstType = visitRelExp(node.getRelExps().get(0));
        if (node.getOperators().isEmpty()) {
            return firstType;
        } else {
            for (int i = 1; i < node.getRelExps().size(); i++) {
                visitRelExp(node.getRelExps().get(i));
            }
            return SymbolType.Int;
        }
    }

    // 关系表达式 RelExp → AddExp | RelExp ('<' | '>' | '<=' | '>=') AddExp
    public SymbolType visitRelExp(RelExpNode node) {
        if (node == null) return null;
        SymbolType firstType = visitAddExp(node.getAddExps().get(0));
        if (node.getOperators().isEmpty()) {
            return firstType;
        } else {
            for (int i = 1; i < node.getAddExps().size(); i++) {
                visitAddExp(node.getAddExps().get(i));
            }
            return SymbolType.Int;
        }
    }

    // 加减表达式 AddExp → MulExp | AddExp ('+' | '−') MulExp
    public SymbolType visitAddExp(AddExpNode node) {
        if (node == null) return null;
        SymbolType firstType = visitMulExp(node.getMulExps().get(0));
        if (node.getOperators().isEmpty()) {
            return firstType;
        } else {
            for (int i = 1; i < node.getMulExps().size(); i++) {
                visitMulExp(node.getMulExps().get(i));
            }
            return SymbolType.Int;
        }
    }

    // 乘除模表达式 MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
    public SymbolType visitMulExp(MulExpNode node) {
        if (node == null) return null;
        SymbolType firstType = visitUnaryExp(node.getUnaryExps().get(0));
        if (node.getOperators().isEmpty()) {
            return firstType;
        } else {
            for (int i = 1; i < node.getUnaryExps().size(); i++) {
                visitUnaryExp(node.getUnaryExps().get(i));
            }
            return SymbolType.Int;
        }
    }

    // 一元表达式 UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp // c d e
    public SymbolType visitUnaryExp(UnaryExpNode node) {
        if (node == null) return null;

        switch (node.getType()) {
            case PRIMARY:
                return visitPrimaryExp(node.getPrimaryExp());
            case UNARY_OP:
                visitUnaryExp(node.getUnaryExp());
                return SymbolType.Int;
            case FUNC_CALL:
                Token ident = node.getIdent();
                Symbol symbol = hub.getCurrentScope().lookup(ident.getText());
                if (symbol == null) {
                    ErrorReporter.addError(ident.getLineNumber(), 'c');
                    return null;
                }
                if (!(symbol instanceof FuncSymbol fs)) { // 找到了，但是变量
                    ErrorReporter.addError(ident.getLineNumber(), 'c');
                    return null;
                }
                int expectedCount = fs.getParameters().size();
                int actualCount = (node.getFuncRParams() == null) ? 0 : node.getFuncRParams().getParams().size();
                if (expectedCount != actualCount) {
                    ErrorReporter.addError(ident.getLineNumber(), 'd');
                }

                if (node.getFuncRParams() != null && expectedCount == actualCount) {
                    List<ValueSymbol> expectedParams = fs.getParameters();
                    List<ExpNode> actualParams = node.getFuncRParams().getParams();
                    for (int i = 0; i < expectedCount; i++) {
                        SymbolType expectedType = expectedParams.get(i).getType();
                        SymbolType actualType = visitExp(actualParams.get(i));
                        if (actualType != null && !areTypesCompatible(expectedType, actualType)) {
                            ErrorReporter.addError(node.getIdent().getLineNumber(), 'e');
                        }
                    }
                }
                if (fs.getReturnType() == SymbolType.IntFunc) {
                    return SymbolType.Int;
                } else {
                    return SymbolType.VoidFunc;
                }
        }
        return null;
    }

    // 基本表达式 PrimaryExp → '(' Exp ')' | LVal | Number
    public SymbolType visitPrimaryExp(PrimaryExpNode node) {
        if (node == null) return null;

        return switch (node.getType()) {
            case LVAL -> {
                LValAnalysis lvalInfo = visitLVal(node.getLval());
                yield (lvalInfo != null) ? lvalInfo.resultingType() : null;
            }
            case PAREN_EXP -> visitExp(node.getExp());
            case NUMBER -> SymbolType.Int;
        };
    }

    private boolean areTypesCompatible(SymbolType expected, SymbolType actual) {
        if (expected == null || actual == null) {
            return false;
        }
        if (expected == SymbolType.Int) {
            return actual == SymbolType.Int ||
                    actual == SymbolType.ConstInt ||
                    actual == SymbolType.StaticInt;
        }
        if (expected == SymbolType.IntArray) {
            // 普通常量数组 (ConstIntArray)不可以传递
            return actual == SymbolType.IntArray ||
                    actual == SymbolType.StaticIntArray;
        }
        return expected.equals(actual);
    }

    public int evalConstExp(ConstExpNode node) {
        if (node == null) return 0; // 或抛出异常
        return evalAddExp(node.getAddExp());
    }

    private int evalAddExp(AddExpNode node) {
        int lhsVal = evalMulExp(node.getMulExps().get(0));

        for (int i = 0; i < node.getOperators().size(); i++) {
            int rhsVal = evalMulExp(node.getMulExps().get(i + 1));
            Token op = node.getOperators().get(i);

            if (op.getType() == TokenType.PLUS) {
                lhsVal = lhsVal + rhsVal;
            } else if (op.getType() == TokenType.MINU) {
                lhsVal = lhsVal - rhsVal;
            }
        }
        return lhsVal;
    }

    private int evalMulExp(MulExpNode node) {
        int lhsVal = evalUnaryExp(node.getUnaryExps().get(0));

        for (int i = 0; i < node.getOperators().size(); i++) {
            int rhsVal = evalUnaryExp(node.getUnaryExps().get(i + 1));
            Token op = node.getOperators().get(i);

            if (op.getType() == TokenType.MULT) {
                lhsVal = lhsVal * rhsVal;
            } else if (op.getType() == TokenType.DIV) {
                lhsVal = lhsVal / rhsVal; 
            } else if (op.getType() == TokenType.MOD) {
                lhsVal = lhsVal % rhsVal;
            } /*else if (op.getType() == TokenType.BITAND) {
                lhsVal = lhsVal & rhsVal;
            } */
            /* [NEW3] ** 运算符常量求值: a**b = (a+b)^b
            else if (op.getType() == TokenType.POWER) {
                int base = lhsVal + rhsVal;
                int result = 1;
                for (int j = 0; j < rhsVal; j++) {
                    result *= base;
                }
                lhsVal = result;
            }
            */
        }
        return lhsVal;
    }

    private int evalUnaryExp(UnaryExpNode node) {
        switch (node.getType()) {
            case PRIMARY:
                return evalPrimaryExp(node.getPrimaryExp());
            case UNARY_OP:
                int val = evalUnaryExp(node.getUnaryExp());
                if (node.getUnaryOp().getOp().getType() == TokenType.MINU) {
                    return -val;
                }
                /* [NEW2] 常量计算支持 ++ 运算符
                else if (node.getUnaryOp().getOp().getType() == TokenType.INCR) {
                    return val + 1; // ++3 = 4
                }
                */
                else {
                    return val; // '+' or '!' (在 ConstExp 中 ! 是非法的)
                }
            case FUNC_CALL:
            default:
                // SysY 规定 ConstExp 不能包含函数调用
                // SemanticVisitor 应该已报错，但我们这里返回 0 以防万一
                return 0;
        }
    }

    private int evalPrimaryExp(PrimaryExpNode node) {
        switch (node.getType()) {
            case NUMBER:
                // 直接解析字面量
                String numStr = node.getNumber().getIntConst().getText();
                return Integer.parseInt(numStr);
            case PAREN_EXP:
                // 递归
                return evalAddExp(node.getExp().getAddExp());
            case LVAL:
                // [关键] 查找已定义的常量
                Symbol symbol = hub.getCurrentScope().lookup(node.getLval().getIdent().getText());
                if (symbol instanceof ValueSymbol vs && vs.isConst() && vs.getConstValue() != null) {
                    // TODO: 仅支持标量 const int N = 10;
                    // 不支持 const int a[10]; ... arr[a[0]] (这在 SysY 中是非法的)
                    return vs.getConstValue();
                }
                // 访问了非常量 (如变量) 或未定义符号
                return 0; // SemanticVisitor 应该已报错
        }
        return 0;
    }
}