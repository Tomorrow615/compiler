package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

// 代表 LAndExp → EqExp { '&&' EqExp }
public class LAndExpNode extends ASTNode {
    private final List<EqExpNode> eqExps;
    private final List<Token> operators;

    public LAndExpNode(List<EqExpNode> eqExps, List<Token> operators) {
        super(eqExps.get(0).getLineNumber());
        this.eqExps = eqExps;
        this.operators = operators;
    }
}
