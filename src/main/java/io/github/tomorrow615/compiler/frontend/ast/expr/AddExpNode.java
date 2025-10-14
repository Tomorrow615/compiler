package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

// 代表 AddExp → MulExp { ('+' | '−') MulExp }
public class AddExpNode extends ASTNode {
    // 第一个 MulExp
    private final MulExpNode firstMulExp;
    // 后续的 { op MulExp } 列表
    private final List<Token> operators; // 存储 '+' 或 '-'
    private final List<MulExpNode> followingMulExps;

    public AddExpNode(MulExpNode firstMulExp, List<Token> operators, List<MulExpNode> followingMulExps) {
        super(firstMulExp.getLineNumber());
        this.firstMulExp = firstMulExp;
        this.operators = operators;
        this.followingMulExps = followingMulExps;
    }

    // 我们可以加一个辅助方法判断它是否只是一个简单的 MulExp
    public boolean isSingleMulExp() {
        return operators.isEmpty();
    }
}