package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.expr.CondNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class IfStmtNode extends StmtNode {
    // 1. 添加 enum
    public enum IfType {
        IF_THEN,        // 只有 then 分支
        IF_THEN_ELSE    // 有 else 分支
    }

    private final IfType ifType;

    private final Token ifToken;
    private final Token lparen;
    private final CondNode cond;
    private final Token rparen;
    private final StmtNode thenStmt;

    // 这两个字段只在 IF_THEN_ELSE 类型下有意义
    private final Token elseToken;
    private final StmtNode elseStmt;

    // 2. 为 "if-then" 创建构造函数
    public IfStmtNode(Token ifToken, Token lparen, CondNode cond, Token rparen, StmtNode thenStmt) {
        super(ifToken.getLineNumber());
        this.ifType = IfType.IF_THEN;
        this.ifToken = ifToken;
        this.lparen = lparen;
        this.cond = cond;
        this.rparen = rparen;
        this.thenStmt = thenStmt;
        this.elseToken = null;
        this.elseStmt = null;
    }

    // 3. 为 "if-then-else" 创建构造函数
    public IfStmtNode(Token ifToken, Token lparen, CondNode cond, Token rparen, StmtNode thenStmt, Token elseToken, StmtNode elseStmt) {
        super(ifToken.getLineNumber());
        this.ifType = IfType.IF_THEN_ELSE;
        this.ifToken = ifToken;
        this.lparen = lparen;
        this.cond = cond;
        this.rparen = rparen;
        this.thenStmt = thenStmt;
        this.elseToken = elseToken;
        this.elseStmt = elseStmt;
    }

    // 4. (推荐) 添加 getter
    public IfType getIfType() {
        return ifType;
    }
}