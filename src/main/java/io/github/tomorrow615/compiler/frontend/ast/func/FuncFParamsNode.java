package io.github.tomorrow615.compiler.frontend.ast.func;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class FuncFParamsNode extends ASTNode {
    private final List<FuncFParamNode> params;
    private final List<Token> commas;

    public FuncFParamsNode(List<FuncFParamNode> params, List<Token> commas) {
        // 以第一个参数的行号为准
        super(params.get(0).getLineNumber());
        this.params = params;
        this.commas = commas;
    }
}