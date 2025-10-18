package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;
import java.util.Collections;

public class LValNode extends ASTNode {

    public enum Type {
        SCALAR,         // 普通变量
        ARRAY_ELEMENT   // 数组元素
    }
    private final Type lvalType;
    private final Token ident;

    private final List<ExpNode> arrayExps;

    public LValNode(Token ident) {
        super(ident.getLineNumber());
        this.lvalType = Type.SCALAR;
        this.ident = ident;
        this.arrayExps = Collections.emptyList(); // 使用不可变的空列表，更安全
    }

    public LValNode(Token ident, List<ExpNode> arrayExps) {
        super(ident.getLineNumber());
        this.lvalType = Type.ARRAY_ELEMENT;
        this.ident = ident;
        this.arrayExps = arrayExps;
    }

    public Type getType() {
        return lvalType;
    }

    public Token getIdent() {
        return ident;
    }

    public List<ExpNode> getArrayExps() {
        return arrayExps;
    }
}