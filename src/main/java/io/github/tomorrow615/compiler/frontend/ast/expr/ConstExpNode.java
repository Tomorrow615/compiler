package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;

public class ConstExpNode extends ASTNode {
    private final AddExpNode addExp;

    public ConstExpNode(AddExpNode addExp) {
        super(addExp.getLineNumber());
        this.addExp = addExp;
    }
}
