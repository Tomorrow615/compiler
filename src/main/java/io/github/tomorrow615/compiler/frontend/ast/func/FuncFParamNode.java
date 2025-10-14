package io.github.tomorrow615.compiler.frontend.ast.func;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.decl.BTypeNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class FuncFParamNode extends ASTNode {
    private final BTypeNode bType;
    private final Token ident;
    // 用于数组参数 int arr[]
    private final Token lbrack; // 可能为 null
    private final Token rbrack; // 可能为 null

    public FuncFParamNode(BTypeNode bType, Token ident, Token lbrack, Token rbrack) {
        super(bType.getLineNumber());
        this.bType = bType;
        this.ident = ident;
        this.lbrack = lbrack;
        this.rbrack = rbrack;
    }
}