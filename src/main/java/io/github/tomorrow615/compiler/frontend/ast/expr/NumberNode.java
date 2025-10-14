package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class NumberNode extends ASTNode {
    private final Token intConst;

    public NumberNode(Token intConst) {
        super(intConst.getLineNumber());
        this.intConst = intConst;
    }
}
