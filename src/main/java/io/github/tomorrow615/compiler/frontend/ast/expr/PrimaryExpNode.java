package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;

public class PrimaryExpNode extends ASTNode {
    public enum Type {
        PAREN_EXP, LVAL, NUMBER
    }
    private final Type type;
    private final ExpNode exp; // for (Exp)
    private final LValNode lval; // for LVal
    private final NumberNode number; // for Number

    public PrimaryExpNode(ExpNode exp) {
        super(exp.getLineNumber());
        this.type = Type.PAREN_EXP;
        this.exp = exp;
        this.lval = null;
        this.number = null;
    }

    public PrimaryExpNode(LValNode lval) {
        super(lval.getLineNumber());
        this.type = Type.LVAL;
        this.exp = null;
        this.lval = lval;
        this.number = null;
    }

    public PrimaryExpNode(NumberNode number) {
        super(number.getLineNumber());
        this.type = Type.NUMBER;
        this.exp = null;
        this.lval = null;
        this.number = number;
    }
}
