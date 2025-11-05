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
}