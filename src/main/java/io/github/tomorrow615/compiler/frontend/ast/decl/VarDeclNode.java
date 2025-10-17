package io.github.tomorrow615.compiler.frontend.ast.decl;

import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class VarDeclNode extends DeclNode {
    private final Token staticToken;
    private final BTypeNode bType;
    private final List<VarDefNode> varDefs;
    private final Token semicn;

    public VarDeclNode(Token staticToken, BTypeNode bType, List<VarDefNode> varDefs, Token semicn) {
        super(staticToken != null ? staticToken.getLineNumber() : bType.getLineNumber());
        this.staticToken = staticToken;
        this.bType = bType;
        this.varDefs = varDefs;
        this.semicn = semicn;
    }
}
