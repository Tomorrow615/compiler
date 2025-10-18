package io.github.tomorrow615.compiler.frontend.ast.decl;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.ConstExpNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class ConstDefNode extends ASTNode {
    private final Token ident;
    private final List<ConstExpNode> constExps;
    private final ConstInitValNode constInitVal;

    public ConstDefNode(Token ident, List<ConstExpNode> constExps, ConstInitValNode constInitVal) {
        super(ident.getLineNumber());
        this.ident = ident;
        this.constExps = constExps;
        this.constInitVal = constInitVal;
    }

    public Token getIdent() {
        return ident;
    }

    public List<ConstExpNode> getConstExps() {
        return constExps;
    }

    public ConstInitValNode getConstInitVal() {
        return constInitVal;
    }
}