package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.expr.ExpNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class ReturnStmtNode extends StmtNode {
    private final Token returnToken;
    private final ExpNode exp; // 可能为 null
    private final Token semicn;

    public ReturnStmtNode(Token returnToken, ExpNode exp, Token semicn) {
        super(returnToken.getLineNumber());
        this.returnToken = returnToken;
        this.exp = exp;
        this.semicn = semicn;
    }
}
