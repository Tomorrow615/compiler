package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

// 代表 MulExp → UnaryExp { ('*' | '/' | '%') UnaryExp }
public class MulExpNode extends ASTNode {
    private final List<UnaryExpNode> unaryExps;
    private final List<Token> operators;

    public MulExpNode(List<UnaryExpNode> unaryExps, List<Token> operators) {
        super(unaryExps.get(0).getLineNumber());
        this.unaryExps = unaryExps;
        this.operators = operators;
    }
}
