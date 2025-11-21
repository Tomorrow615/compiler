package io.github.tomorrow615.compiler.midend.irgen;

import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.lexer.TokenType;
import io.github.tomorrow615.compiler.frontend.symbol.Symbol;
import io.github.tomorrow615.compiler.frontend.symbol.ValueSymbol;

/**
 * 编译期常量求值器
 * 负责计算 ConstExp 的值，用于全局变量初始化和数组维度定义
 */
public class ConstEvaluator {
    private final IRGenContext context;

    public ConstEvaluator(IRGenContext context) {
        this.context = context;
    }

    public int eval(ConstExpNode node) {
        return calcAddExp(node.getAddExp());
    }

    // 递归计算辅助方法
    private int calcAddExp(AddExpNode node) {
        int val = calcMulExp(node.getMulExps().get(0));
        for (int i = 0; i < node.getOperators().size(); i++) {
            int rhs = calcMulExp(node.getMulExps().get(i + 1));
            if (node.getOperators().get(i).getType() == TokenType.PLUS) {
                val += rhs;
            } else {
                val -= rhs;
            }
        }
        return val;
    }

    private int calcMulExp(MulExpNode node) {
        int val = calcUnaryExp(node.getUnaryExps().get(0));
        for (int i = 0; i < node.getOperators().size(); i++) {
            int rhs = calcUnaryExp(node.getUnaryExps().get(i + 1));
            TokenType op = node.getOperators().get(i).getType();
            if (op == TokenType.MULT) {
                val *= rhs;
            } else if (op == TokenType.DIV) {
                val = (rhs != 0) ? val / rhs : 0;
            } else if (op == TokenType.MOD) {
                val = (rhs != 0) ? val % rhs : 0;
            }
        }
        return val;
    }

    private int calcUnaryExp(UnaryExpNode node) {
        if (node.getType() == UnaryExpNode.Type.PRIMARY) {
            return calcPrimaryExp(node.getPrimaryExp());
        } else if (node.getType() == UnaryExpNode.Type.UNARY_OP) {
            int val = calcUnaryExp(node.getUnaryExp());
            TokenType op = node.getUnaryOp().getOp().getType();
            if (op == TokenType.MINU) return -val;
            if (op == TokenType.NOT) return (val == 0) ? 1 : 0;
            return val; // PLUS
        }
        return 0;
    }

    private int calcPrimaryExp(PrimaryExpNode node) {
        if (node.getType() == PrimaryExpNode.Type.NUMBER) {
            return Integer.parseInt(node.getNumber().getIntConst().getText());
        } else if (node.getType() == PrimaryExpNode.Type.PAREN_EXP) {
            return calcAddExp(node.getExp().getAddExp());
        } else if (node.getType() == PrimaryExpNode.Type.LVAL) {
            LValNode lval = node.getLval();
            // [关键] 使用 context 获取当前作用域进行查找
            Symbol sym = context.getCurrentScope().lookup(lval.getIdent().getText());

            if (sym instanceof ValueSymbol valSym && valSym.isConst()) {
                if (lval.getType() == LValNode.Type.SCALAR) {
                    if (valSym.getConstValue() != null) {
                        return valSym.getConstValue();
                    }
                } else {
                    // 数组元素常量折叠
                    ExpNode indexExp = lval.getArrayExps().get(0);
                    int index = calcAddExp(indexExp.getAddExp());

                    Integer val = valSym.getConstArrayValue(index);
                    if (val != null) {
                        return val;
                    }
                }
            }
            return 0;
        }
        return 0;
    }
}