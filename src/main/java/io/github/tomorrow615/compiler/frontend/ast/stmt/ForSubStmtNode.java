package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.ExpNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.LValNode;
import java.util.List;

public class ForSubStmtNode extends ASTNode {
    private final List<LValNode> lVals;
    private final List<ExpNode> exps;

    public ForSubStmtNode(List<LValNode> lVals, List<ExpNode> exps) {
        super(lVals.get(0).getLineNumber());
        this.lVals = lVals;
        this.exps = exps;
    }

    public List<LValNode> getLVals() {
        return lVals;
    }

    public List<ExpNode> getExps() {
        return exps;
    }
}
