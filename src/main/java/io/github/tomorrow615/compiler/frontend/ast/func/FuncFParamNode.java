package io.github.tomorrow615.compiler.frontend.ast.func;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.decl.BTypeNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class FuncFParamNode extends ASTNode {

    public enum Type {
        SCALAR, // 普通变量
        ARRAY   // 数组变量
    }
    private final Type paramType;

    private final BTypeNode bType;
    private final Token ident;

    public FuncFParamNode(BTypeNode bType, Token ident) {
        super(bType.getLineNumber());
        this.paramType = Type.SCALAR;
        this.bType = bType;
        this.ident = ident;
    }

    public FuncFParamNode(BTypeNode bType, Token ident, boolean isArray) {
        super(bType.getLineNumber());
        this.paramType = isArray ? Type.ARRAY : Type.SCALAR;
        this.bType = bType;
        this.ident = ident;
    }

    public Type getType() {
        return paramType;
    }

    public BTypeNode getBType() {
        return bType;
    }

    public Token getIdent() {
        return ident;
    }
}