package io.github.tomorrow615.compiler.frontend.ast.decl;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.ConstExpNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class VarDefNode extends ASTNode {
    private final Token ident;
    private final List<Token> lBracks;
    private final List<ConstExpNode> constExps;
    private final List<Token> rBracks;
    // VarDef 的关键区别：Assign 和 InitVal 是可选的
    private final Token assignToken; // '='
    private final InitValNode initVal;

    public VarDefNode(Token ident, List<Token> lBracks, List<ConstExpNode> constExps, List<Token> rBracks, Token assignToken, InitValNode initVal) {
        super(ident.getLineNumber());
        this.ident = ident;
        this.lBracks = lBracks;
        this.constExps = constExps;
        this.rBracks = rBracks;
        this.assignToken = assignToken;
        this.initVal = initVal;
    }
}
