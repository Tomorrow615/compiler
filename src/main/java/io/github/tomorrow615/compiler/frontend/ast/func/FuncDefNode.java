package io.github.tomorrow615.compiler.frontend.ast.func;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.stmt.BlockNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class FuncDefNode extends ASTNode {
    private final FuncTypeNode funcType;
    private final Token ident;
    private final Token lparen;
    private final FuncFParamsNode funcFParams; // 可选，可能为 null
    private final Token rparen;
    private final BlockNode block;

    public FuncDefNode(FuncTypeNode funcType, Token ident, Token lparen, FuncFParamsNode funcFParams, Token rparen, BlockNode block) {
        super(funcType.getLineNumber());
        this.funcType = funcType;
        this.ident = ident;
        this.lparen = lparen;
        this.funcFParams = funcFParams;
        this.rparen = rparen;
        this.block = block;
    }
}
