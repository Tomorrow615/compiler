package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.ExpNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.LValNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class ForSubStmtNode extends ASTNode {
    // for (i = 0, j = 1; ... )
    private final List<LValNode> lVals;
    private final List<Token> assignTokens;
    private final List<ExpNode> exps;
    private final List<Token> commas;

    public ForSubStmtNode(List<LValNode> lVals, List<Token> assignTokens, List<ExpNode> exps, List<Token> commas) {
        super(lVals.get(0).getLineNumber());
        this.lVals = lVals;
        this.assignTokens = assignTokens;
        this.exps = exps;
        this.commas = commas;
    }
}
