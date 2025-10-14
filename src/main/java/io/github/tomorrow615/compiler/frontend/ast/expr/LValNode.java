package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class LValNode extends ASTNode {
    private final Token ident;
    private final List<Token> lBracks;
    private final List<ExpNode> arrayExps;
    private final List<Token> rBracks;

    public LValNode(Token ident, List<Token> lBracks, List<ExpNode> arrayExps, List<Token> rBracks) {
        // REFACTOR: 增加 null 判断以增强鲁棒性
        super(ident != null ? ident.getLineNumber() : -1);
        this.ident = ident;
        this.lBracks = lBracks;
        this.arrayExps = arrayExps;
        this.rBracks = rBracks;
    }
}
