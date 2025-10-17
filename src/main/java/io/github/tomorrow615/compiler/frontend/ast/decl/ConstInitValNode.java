package io.github.tomorrow615.compiler.frontend.ast.decl;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.ConstExpNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

import java.util.List;

public class ConstInitValNode extends ASTNode {

    public enum Type {
        SINGLE,
        ARRAY
    }

    private final Type type;

    // 用于 single a = 10;
    private ConstExpNode singleInit;

    // 用于 array a[2] = {1, 2};
    private Token lBrace; // {
    private List<ConstExpNode> arrayInit;
    private List<Token> commas; // ,
    private Token rBrace; // }

    // 构造函数 for single value
    public ConstInitValNode(ConstExpNode singleInit) {
        super(singleInit.getLineNumber());
        this.type = Type.SINGLE;
        this.singleInit = singleInit;
    }

    // 构造函数 for array initializer
    public ConstInitValNode(Token lBrace, List<ConstExpNode> arrayInit, List<Token> commas, Token rBrace) {
        super(lBrace.getLineNumber());
        this.type = Type.ARRAY;
        this.lBrace = lBrace;
        this.arrayInit = arrayInit;
        this.commas = commas;
        this.rBrace = rBrace;
    }
}