package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.expr.ExpNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class ExpStmtNode extends StmtNode {
    private final ExpNode exp; // 可能为 null (对应 ';' 的情况)
    private final Token semicn;

    public ExpStmtNode(ExpNode exp, Token semicn) {
        // 如果 exp 为 null，行号就取分号的行号
        super(exp != null ? exp.getLineNumber() : (semicn != null ? semicn.getLineNumber() : -1));
        this.exp = exp;
        this.semicn = semicn;
    }
}
