package io.github.tomorrow615.compiler.frontend.ast.decl;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.ConstExpNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class ConstDefNode extends ASTNode {
    private final Token ident;
    private final List<Token> lBracks;
    private final List<ConstExpNode> constExps;
    private final List<Token> rBracks;
    private final Token assignToken; // '='
    private final ConstInitValNode constInitVal;

    public ConstDefNode(Token ident, List<Token> lBracks, List<ConstExpNode> constExps, List<Token> rBracks, Token assignToken, ConstInitValNode constInitVal) {
        super(ident.getLineNumber());
        this.ident = ident;
        this.lBracks = lBracks;
        this.constExps = constExps;
        this.rBracks = rBracks;
        this.assignToken = assignToken;
        this.constInitVal = constInitVal;
    }
}