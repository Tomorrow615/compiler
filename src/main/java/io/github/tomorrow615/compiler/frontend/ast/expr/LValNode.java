package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class LValNode extends ASTNode {

    // 1. 添加 enum 来明确区分
    public enum LValType {
        SCALAR,         // 普通变量
        ARRAY_ELEMENT   // 数组元素
    }
    private final LValType lvalType;

    private final Token ident;
    // 下面的字段只在 ARRAY_ELEMENT 类型下有意义
    private final List<Token> lBracks;
    private final List<ExpNode> arrayExps;
    private final List<Token> rBracks;

    // 2. 为普通变量创建构造函数
    public LValNode(Token ident) {
        super(ident.getLineNumber());
        this.lvalType = LValType.SCALAR;
        this.ident = ident;
        this.lBracks = List.of(); // 使用不可变空列表，更安全
        this.arrayExps = List.of();
        this.rBracks = List.of();
    }

    // 3. 为数组元素创建构造函数
    public LValNode(Token ident, List<Token> lBracks, List<ExpNode> arrayExps, List<Token> rBracks) {
        super(ident.getLineNumber());
        this.lvalType = LValType.ARRAY_ELEMENT;
        this.ident = ident;
        this.lBracks = lBracks;
        this.arrayExps = arrayExps;
        this.rBracks = rBracks;
    }

    // 4. (推荐) 添加 getter
    public LValType getLvalType() {
        return lvalType;
    }
}
