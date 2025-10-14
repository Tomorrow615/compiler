package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class BreakStmtNode extends StmtNode {
    private final Token breakToken;
    private final Token semicn;

    public BreakStmtNode(Token breakToken, Token semicn) {
        super(breakToken.getLineNumber());
        this.breakToken = breakToken;
        this.semicn = semicn;
    }
}
