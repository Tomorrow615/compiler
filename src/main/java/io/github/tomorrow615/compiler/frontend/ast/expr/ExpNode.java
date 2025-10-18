package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;

public class ExpNode extends ASTNode {
    private final AddExpNode addExp;

    public ExpNode(AddExpNode addExp) {
        super(addExp.getLineNumber());
        this.addExp = addExp;
    }

    public AddExpNode getAddExp() {
        return addExp;
    }
}
