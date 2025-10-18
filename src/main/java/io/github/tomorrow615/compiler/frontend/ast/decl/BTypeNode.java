package io.github.tomorrow615.compiler.frontend.ast.decl;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class BTypeNode extends ASTNode {
    private final Token typeToken;

    public BTypeNode(Token typeToken) {
        super(typeToken.getLineNumber());
        this.typeToken = typeToken;
    }

    public Token getTypeToken(){
        return typeToken;
    }
}