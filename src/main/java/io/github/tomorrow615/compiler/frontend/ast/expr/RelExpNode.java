package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class RelExpNode extends ASTNode {
    private final List<AddExpNode> addExps;
    private final List<Token> operators;

    public RelExpNode(List<AddExpNode> addExps, List<Token> operators) {
        super(addExps.get(0).getLineNumber());
        this.addExps = addExps;
        this.operators = operators;
    }

    public List<AddExpNode> getAddExps() {
        return addExps;
    }

    public List<Token> getOperators() {
        return operators;
    }
}
