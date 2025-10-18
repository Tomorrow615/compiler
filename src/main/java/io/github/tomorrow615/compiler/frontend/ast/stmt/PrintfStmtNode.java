package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.expr.ExpNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class PrintfStmtNode extends StmtNode {
    private final Token formatString; // StringConst
    private final List<ExpNode> exps;

    public PrintfStmtNode(Token formatString, List<ExpNode> exps, int lineNumber) {
        super(lineNumber);
        this.formatString = formatString;
        this.exps = exps;
    }

    public Token getFormatString() {
        return formatString;
    }

    public List<ExpNode> getExps() {
        return exps;
    }
}
