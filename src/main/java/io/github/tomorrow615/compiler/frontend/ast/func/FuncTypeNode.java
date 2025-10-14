package io.github.tomorrow615.compiler.frontend.ast.func;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class FuncTypeNode extends ASTNode {
    private final Token typeToken; // 'void' or 'int'

    public FuncTypeNode(Token typeToken) {
        super(typeToken.getLineNumber());
        this.typeToken = typeToken;
    }
}