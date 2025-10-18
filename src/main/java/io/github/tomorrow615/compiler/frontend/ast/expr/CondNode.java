package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;

public class CondNode extends ASTNode {
    private final LOrExpNode lorExp;

    public CondNode(LOrExpNode lorExp) {
        super(lorExp.getLineNumber());
        this.lorExp = lorExp;
    }

    public LOrExpNode getLorExp() {
        return lorExp;
    }
}