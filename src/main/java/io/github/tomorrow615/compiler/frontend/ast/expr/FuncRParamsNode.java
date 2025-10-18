package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import java.util.List;

public class FuncRParamsNode extends ASTNode {
    private final List<ExpNode> params;

    public FuncRParamsNode(List<ExpNode> params) {
        super(params.get(0).getLineNumber());
        this.params = params;
    }

    public List<ExpNode> getParams() {
        return params;
    }
}
