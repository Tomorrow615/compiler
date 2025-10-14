package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class FuncRParamsNode extends ASTNode {
    private final List<ExpNode> params;
    private final List<Token> commas;
    public FuncRParamsNode(List<ExpNode> params, List<Token> commas) {
        super(params.get(0).getLineNumber());
        this.params = params;
        this.commas = commas;
    }
}
