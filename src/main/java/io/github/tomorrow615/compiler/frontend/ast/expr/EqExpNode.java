package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class EqExpNode extends ASTNode {
    private final List<RelExpNode> relExps;
    private final List<Token> operators;

    public EqExpNode(List<RelExpNode> relExps, List<Token> operators) {
        super(relExps.get(0).getLineNumber());
        this.relExps = relExps;
        this.operators = operators;
    }

    public List<RelExpNode> getRelExps() {
        return relExps;
    }

    public List<Token> getOperators() {
        return operators;
    }
}