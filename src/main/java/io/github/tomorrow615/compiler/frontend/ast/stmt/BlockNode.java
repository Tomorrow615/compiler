package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
// 我们后面会用到 List，所以提前 import
import java.util.List;

public class BlockNode extends ASTNode {
    private final Token lBrace;
    // TODO: Phase 2 will add a list of BlockItems here
    private final Token rBrace;

    public BlockNode(Token lBrace, Token rBrace) {
        super(lBrace.getLineNumber());
        this.lBrace = lBrace;
        this.rBrace = rBrace;
    }
}