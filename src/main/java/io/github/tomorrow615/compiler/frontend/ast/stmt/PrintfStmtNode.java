package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.expr.ExpNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class PrintfStmtNode extends StmtNode {
    private final Token printfToken;
    private final Token lparen;
    private final Token formatString; // StringConst
    private final List<Token> commas;
    private final List<ExpNode> exps;
    private final Token rparen;
    private final Token semicn;

    public PrintfStmtNode(Token printfToken, Token lparen, Token formatString, List<Token> commas, List<ExpNode> exps, Token rparen, Token semicn) {
        super(printfToken.getLineNumber());
        this.printfToken = printfToken;
        this.lparen = lparen;
        this.formatString = formatString;
        this.commas = commas;
        this.exps = exps;
        this.rparen = rparen;
        this.semicn = semicn;
    }
}
