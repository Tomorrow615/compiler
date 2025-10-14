package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.expr.CondNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class ForStmtNode extends StmtNode {
    private final Token forToken;
    private final Token lparen;
    private final ForSubStmtNode initStmt;    // for(HERE;...;...) 可为 null
    private final Token firstSemicn;
    private final CondNode cond;              // for(...;HERE;...) 可为 null
    private final Token secondSemicn;
    private final ForSubStmtNode updateStmt;  // for(...;...;HERE) 可为 null
    private final Token rparen;
    private final StmtNode bodyStmt;          // 循环体

    public ForStmtNode(Token forToken, Token lparen, ForSubStmtNode initStmt, Token firstSemicn, CondNode cond, Token secondSemicn, ForSubStmtNode updateStmt, Token rparen, StmtNode bodyStmt) {
        super(forToken.getLineNumber());
        this.forToken = forToken;
        this.lparen = lparen;
        this.initStmt = initStmt;
        this.firstSemicn = firstSemicn;
        this.cond = cond;
        this.secondSemicn = secondSemicn;
        this.updateStmt = updateStmt;
        this.rparen = rparen;
        this.bodyStmt = bodyStmt;
    }
}
