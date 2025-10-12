package io.github.tomorrow615.compiler.frontend.ast.decl;

import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class ConstDeclNode extends DeclNode {
    private final Token constToken; // 'const'
    private final BTypeNode bType;
    private final List<ConstDefNode> constDefs;
    private final Token semicn;     // ';'

    public ConstDeclNode(Token constToken, BTypeNode bType, List<ConstDefNode> constDefs, Token semicn, int lineNumber) {
        super(lineNumber);
        this.constToken = constToken;
        this.bType = bType;
        this.constDefs = constDefs;
        this.semicn = semicn;
    }
}