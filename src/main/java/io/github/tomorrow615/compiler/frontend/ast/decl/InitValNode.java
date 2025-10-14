package io.github.tomorrow615.compiler.frontend.ast.decl;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.ExpNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class InitValNode extends ASTNode {
    public enum Type {
        SINGLE, ARRAY
    }

    private final Type type;
    private ExpNode singleInit;
    private Token lBrace;
    private List<ExpNode> arrayInit;
    private List<Token> commas;
    private Token rBrace;

    // 构造函数 for single value
    public InitValNode(ExpNode singleInit) {
        super(singleInit.getLineNumber());
        this.type = Type.SINGLE;
        this.singleInit = singleInit;
    }

    // 构造函数 for array initializer
    public InitValNode(Token lBrace, List<ExpNode> arrayInit, List<Token> commas, Token rBrace) {
        super(lBrace.getLineNumber());
        this.type = Type.ARRAY;
        this.lBrace = lBrace;
        this.arrayInit = arrayInit;
        this.commas = commas;
        this.rBrace = rBrace;
    }
}
