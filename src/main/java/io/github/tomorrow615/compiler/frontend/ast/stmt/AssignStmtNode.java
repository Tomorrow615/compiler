package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.expr.ExpNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.LValNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class AssignStmtNode extends StmtNode {
    private final LValNode lVal;
    private final Token assignToken; // '='
    private final ExpNode exp;
    private final Token semicn;    // ';'

    public AssignStmtNode(LValNode lVal, Token assignToken, ExpNode exp, Token semicn) {
        super(lVal.getLineNumber());
        this.lVal = lVal;
        this.assignToken = assignToken;
        this.exp = exp;
        this.semicn = semicn;
    }
}
