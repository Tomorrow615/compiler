package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;

public class CondNode extends ASTNode {
    // 根据文法 Cond -> LOrExp，LOrExp 是 Exp 的一部分。
    // 在完成完整的表达式解析前，我们用 ExpNode 来代表条件。
    private final ExpNode exp;

    public CondNode(ExpNode exp) {
        super(exp.getLineNumber());
        this.exp = exp;
    }
}
