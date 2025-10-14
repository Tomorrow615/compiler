package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;

// Exp -> AddExp
public class ExpNode extends ASTNode {
    private final AddExpNode addExp;

    public ExpNode(AddExpNode addExp) {
        super(addExp.getLineNumber());
        this.addExp = addExp;
    }
}
