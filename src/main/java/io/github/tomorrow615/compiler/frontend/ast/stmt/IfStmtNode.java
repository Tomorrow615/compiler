package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.expr.CondNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class IfStmtNode extends StmtNode {
    private final Token ifToken;
    private final Token lparen;
    private final CondNode cond;
    private final Token rparen;
    private final StmtNode thenStmt;
    private final Token elseToken;   // 可能为 null
    private final StmtNode elseStmt;    // 可能为 null

    public IfStmtNode(Token ifToken, Token lparen, CondNode cond, Token rparen, StmtNode thenStmt, Token elseToken, StmtNode elseStmt) {
        super(ifToken.getLineNumber());
        this.ifToken = ifToken;
        this.lparen = lparen;
        this.cond = cond;
        this.rparen = rparen;
        this.thenStmt = thenStmt;
        this.elseToken = elseToken;
        this.elseStmt = elseStmt;
    }
}
