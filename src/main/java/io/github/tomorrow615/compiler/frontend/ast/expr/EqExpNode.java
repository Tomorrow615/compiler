package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

// 代表 EqExp → RelExp { ('==' | '!=') RelExp }
public class EqExpNode extends ASTNode {
    private final RelExpNode firstRelExp;
    private final List<Token> operators;
    private final List<RelExpNode> followingRelExps;

    public EqExpNode(RelExpNode firstRelExp, List<Token> operators, List<RelExpNode> followingRelExps) {
        super(firstRelExp.getLineNumber());
        this.firstRelExp = firstRelExp;
        this.operators = operators;
        this.followingRelExps = followingRelExps;
    }
}