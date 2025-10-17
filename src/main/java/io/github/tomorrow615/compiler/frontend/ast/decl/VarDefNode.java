package io.github.tomorrow615.compiler.frontend.ast.decl;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.ConstExpNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class VarDefNode extends ASTNode {
    // 1. 添加一个 enum 来区分两种情况
    public enum Type {
        UNINITIALIZED, // 无初始值
        INITIALIZED    // 有初始值
    }

    private final Type type;

    private final Token ident;
    private final List<Token> lBracks;
    private final List<ConstExpNode> constExps;
    private final List<Token> rBracks;

    // 这两个字段现在只在 INITIALIZED 类型下有意义
    private final Token assignToken;
    private final InitValNode initVal;

    // 2. 创建第一个构造函数，用于无初始值的情况
    public VarDefNode(Token ident, List<Token> lBracks, List<ConstExpNode> constExps, List<Token> rBracks) {
        super(ident.getLineNumber());
        this.type = Type.UNINITIALIZED;
        this.ident = ident;
        this.lBracks = lBracks;
        this.constExps = constExps;
        this.rBracks = rBracks;
        // 将可选字段设为null
        this.assignToken = null;
        this.initVal = null;
    }

    // 3. 创建第二个构造函数，用于有初始值的情况
    public VarDefNode(Token ident, List<Token> lBracks, List<ConstExpNode> constExps, List<Token> rBracks, Token assignToken, InitValNode initVal) {
        super(ident.getLineNumber());
        this.type = Type.INITIALIZED;
        this.ident = ident;
        this.lBracks = lBracks;
        this.constExps = constExps;
        this.rBracks = rBracks;
        this.assignToken = assignToken;
        this.initVal = initVal;
    }

    // 4. (可选但推荐) 添加一个getter
    public Type getType() {
        return type;
    }
}