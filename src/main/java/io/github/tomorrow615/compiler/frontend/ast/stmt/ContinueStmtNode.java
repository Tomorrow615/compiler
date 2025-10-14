package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class ContinueStmtNode extends StmtNode {
    private final Token continueToken;
    private final Token semicn;

    public ContinueStmtNode(Token continueToken, Token semicn) {
        super(continueToken.getLineNumber());
        this.continueToken = continueToken;
        this.semicn = semicn;
    }
}
