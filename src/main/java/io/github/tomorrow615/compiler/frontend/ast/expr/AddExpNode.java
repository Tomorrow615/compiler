package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class AddExpNode extends ASTNode {
    private final List<MulExpNode> mulExps;
    private final List<Token> operators;

    public AddExpNode(List<MulExpNode> mulExps, List<Token> operators) {
        super(mulExps.get(0).getLineNumber());
        this.mulExps = mulExps;
        this.operators = operators;
    }

    public List<MulExpNode> getMulExps() {
        return mulExps;
    }

    public List<Token> getOperators() {
        return operators;
    }
}