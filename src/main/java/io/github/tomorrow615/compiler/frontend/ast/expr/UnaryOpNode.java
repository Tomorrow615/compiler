package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class UnaryOpNode extends ASTNode {
    private final Token op;
    public UnaryOpNode(Token op) {
        super(op.getLineNumber());
        this.op = op;
    }
}
