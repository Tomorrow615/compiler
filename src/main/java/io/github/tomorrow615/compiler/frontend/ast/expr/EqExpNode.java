package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

// 代表 EqExp → RelExp { ('==' | '!=') RelExp }
public class EqExpNode extends ASTNode {
    // 修改前: firstRelExp 和 followingRelExps
    // 修改后: 统一的 relExps 列表
    private final List<RelExpNode> relExps;
    private final List<Token> operators;

    public EqExpNode(List<RelExpNode> relExps, List<Token> operators) {
        super(relExps.get(0).getLineNumber());
        this.relExps = relExps;
        this.operators = operators;
    }
}