package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class LOrExpNode extends ASTNode {
    private final List<LAndExpNode> lAndExps;
    private final List<Token> operators;

    public LOrExpNode(List<LAndExpNode> lAndExps, List<Token> operators) {
        super(lAndExps.get(0).getLineNumber());
        this.lAndExps = lAndExps;
        this.operators = operators;
    }

    public List<LAndExpNode> getlAndExps() {
        return lAndExps;
    }

    public List<Token> getOperators() {
        return operators;
    }
}
