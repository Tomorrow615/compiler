package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.expr.CondNode;
// [NEW4] import io.github.tomorrow615.compiler.frontend.ast.decl.VarDeclNode;

public class ForStmtNode extends StmtNode {
    private final ForSubStmtNode initStmt;    // for(HERE;...;...) 可为 null
    // [NEW4] private final VarDeclNode initDecl;      // for(int i=1;...;...) 变量声明，可为 null
    private final CondNode cond;              // for(...;HERE;...) 可为 null
    private final ForSubStmtNode updateStmt;  // for(...;...;HERE) 可为 null
    private final StmtNode bodyStmt;          // 循环体

    public ForStmtNode(ForSubStmtNode initStmt, CondNode cond, ForSubStmtNode updateStmt, StmtNode bodyStmt, int lineNumber) {
        super(lineNumber);
        this.initStmt = initStmt;
        // [NEW4] this.initDecl = null;
        this.cond = cond;
        this.updateStmt = updateStmt;
        this.bodyStmt = bodyStmt;
    }

    /* [NEW4] 新构造函数：支持 for(int i=1;;) 语法
    public ForStmtNode(ForSubStmtNode initStmt, VarDeclNode initDecl, CondNode cond, 
                       ForSubStmtNode updateStmt, StmtNode bodyStmt, int lineNumber) {
        super(lineNumber);
        this.initStmt = initStmt;
        this.initDecl = initDecl;
        this.cond = cond;
        this.updateStmt = updateStmt;
        this.bodyStmt = bodyStmt;
    }
    */

    public ForSubStmtNode getInitStmt() {
        return initStmt;
    }

    /* [NEW4] 获取变量声明
    public VarDeclNode getInitDecl() {
        return initDecl;
    }
    */

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
