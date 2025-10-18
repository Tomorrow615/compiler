package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.expr.CondNode;

public class IfStmtNode extends StmtNode {
    public enum Type {
        IF_THEN,        // 只有 then 分支
        IF_THEN_ELSE    // 有 else 分支
    }

    private final Type ifType;
    private final CondNode cond;
    private final StmtNode thenStmt;
    private final StmtNode elseStmt;

    public IfStmtNode(CondNode cond, StmtNode thenStmt, int lineNumber) {
        super(lineNumber);
        this.ifType = Type.IF_THEN;
        this.cond = cond;
        this.thenStmt = thenStmt;
        this.elseStmt = null;
    }

    public IfStmtNode(CondNode cond, StmtNode thenStmt, StmtNode elseStmt, int lineNumber) {
        super(lineNumber);
        this.ifType = Type.IF_THEN_ELSE;
        this.cond = cond;
        this.thenStmt = thenStmt;
        this.elseStmt = elseStmt;
    }

    public Type getType() {
        return ifType;
    }

    public CondNode getCond() {
        return cond;
    }

    public StmtNode getThenStmt() {
        return thenStmt;
    }

    public StmtNode getElseStmt() {
        return elseStmt;
    }
}