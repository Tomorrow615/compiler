package io.github.tomorrow615.compiler.frontend.ast.func;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.stmt.BlockNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class MainFuncDefNode extends ASTNode {
    private final Token intToken;
    private final Token mainToken;
    private final Token lparen;
    private final Token rparen;
    private final BlockNode block;

    public MainFuncDefNode(Token intToken, Token mainToken, Token lparen, Token rparen, BlockNode block) {
        super(intToken.getLineNumber());
        this.intToken = intToken;
        this.mainToken = mainToken;
        this.lparen = lparen;
        this.rparen = rparen;
        this.block = block;
    }
}
