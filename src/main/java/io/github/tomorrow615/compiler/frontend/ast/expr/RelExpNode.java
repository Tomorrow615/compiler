package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

// 代表 RelExp → AddExp { ('<' | '>' | '<=' | '>=') AddExp }
public class RelExpNode extends ASTNode {
    private final AddExpNode firstAddExp;
    private final List<Token> operators;
    private final List<AddExpNode> followingAddExps;

    public RelExpNode(AddExpNode firstAddExp, List<Token> operators, List<AddExpNode> followingAddExps) {
        super(firstAddExp.getLineNumber());
        this.firstAddExp = firstAddExp;
        this.operators = operators;
        this.followingAddExps = followingAddExps;
    }
}
