package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.expr.ExpNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.LValNode;

public class AssignStmtNode extends StmtNode {
    private final LValNode lVal;
    private final ExpNode exp;

    public AssignStmtNode(LValNode lVal, ExpNode exp) {
        super(lVal.getLineNumber());
        this.lVal = lVal;
        this.exp = exp;
    }

    public LValNode getlVal() {
        return lVal;
    }

    public ExpNode getExp() {
        return exp;
    }
}
