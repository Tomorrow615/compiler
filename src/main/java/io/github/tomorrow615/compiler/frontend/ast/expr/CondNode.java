package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;

public class CondNode extends ASTNode {
    // private final ExpNode exp; // <-- 修改前
    private final LOrExpNode lorExp; // <-- 修改后

    public CondNode(LOrExpNode lorExp) { // <-- 修改后
        super(lorExp.getLineNumber());
        this.lorExp = lorExp; // <-- 修改后
    }
}