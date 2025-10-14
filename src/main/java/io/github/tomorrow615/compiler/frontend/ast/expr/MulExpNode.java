package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

// 代表 MulExp → UnaryExp { ('*' | '/' | '%') UnaryExp }
public class MulExpNode extends ASTNode {
    private final UnaryExpNode firstUnaryExp;
    private final List<Token> operators; // 存储 '*', '/', '%'
    private final List<UnaryExpNode> followingUnaryExps;

    public MulExpNode(UnaryExpNode firstUnaryExp, List<Token> operators, List<UnaryExpNode> followingUnaryExps) {
        super(firstUnaryExp.getLineNumber());
        this.firstUnaryExp = firstUnaryExp;
        this.operators = operators;
        this.followingUnaryExps = followingUnaryExps;
    }

    public boolean isSingleUnaryExp() {
        return operators.isEmpty();
    }
}
