package io.github.tomorrow615.compiler.frontend.ast.decl;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class BTypeNode extends ASTNode {
    private final Token typeToken; // 这个字段将保存 'int' 这个Token本身

    public BTypeNode(Token typeToken) {
        super(typeToken.getLineNumber()); // 使用Token的行号来初始化父类
        this.typeToken = typeToken;
    }
}