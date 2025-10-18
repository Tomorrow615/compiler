package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.expr.ExpNode;

public class ExpStmtNode extends StmtNode {
    private final ExpNode exp;

    public ExpStmtNode(ExpNode exp, int lineNumber) {
        super(lineNumber);
        this.exp = exp;
    }

    public ExpNode getExp() {
        return exp;
    }
}
