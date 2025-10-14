package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

// 代表 LAndExp → EqExp { '&&' EqExp }
public class LAndExpNode extends ASTNode {
    private final EqExpNode firstEqExp;
    private final List<Token> operators;
    private final List<EqExpNode> followingEqExps;

    public LAndExpNode(EqExpNode firstEqExp, List<Token> operators, List<EqExpNode> followingEqExps) {
        super(firstEqExp.getLineNumber());
        this.firstEqExp = firstEqExp;
        this.operators = operators;
        this.followingEqExps = followingEqExps;
    }
}
