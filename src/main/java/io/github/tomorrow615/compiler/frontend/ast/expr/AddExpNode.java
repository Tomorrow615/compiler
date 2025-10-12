package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class AddExpNode extends ASTNode {
    // Phase 1: 简化版，只存一个数字 Token
    private final Token number;

    public AddExpNode(Token number) {
        super(number.getLineNumber());
        this.number = number;
    }
}