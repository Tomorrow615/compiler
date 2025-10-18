package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.expr.CondNode;

public class ForStmtNode extends StmtNode {
    private final ForSubStmtNode initStmt;    // for(HERE;...;...) 可为 null
    private final CondNode cond;              // for(...;HERE;...) 可为 null
    private final ForSubStmtNode updateStmt;  // for(...;...;HERE) 可为 null
    private final StmtNode bodyStmt;          // 循环体

    public ForStmtNode(ForSubStmtNode initStmt, CondNode cond, ForSubStmtNode updateStmt, StmtNode bodyStmt, int lineNumber) {
        super(lineNumber);
        this.initStmt = initStmt;
        this.cond = cond;
        this.updateStmt = updateStmt;
        this.bodyStmt = bodyStmt;
    }

    public ForSubStmtNode getInitStmt() {
        return initStmt;
    }

    public CondNode getCond() {
        return cond;
    }

    public ForSubStmtNode getUpdateStmt() {
        return updateStmt;
    }

    public StmtNode getBodyStmt() {
        return bodyStmt;
    }
}
