package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

// 代表 LOrExp → LAndExp { '||' LAndExp }
public class LOrExpNode extends ASTNode {
    private final LAndExpNode firstLAndExp;
    private final List<Token> operators;
    private final List<LAndExpNode> followingLAndExps;

    public LOrExpNode(LAndExpNode firstLAndExp, List<Token> operators, List<LAndExpNode> followingLAndExps) {
        super(firstLAndExp.getLineNumber());
        this.firstLAndExp = firstLAndExp;
        this.operators = operators;
        this.followingLAndExps = followingLAndExps;
    }
}
