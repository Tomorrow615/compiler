package io.github.tomorrow615.compiler.frontend.ast.func;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.decl.BTypeNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class FuncFParamNode extends ASTNode {

    // 1. 添加 enum 来明确区分类型
    public enum ParamType {
        SCALAR, // 普通变量
        ARRAY   // 数组变量
    }
    private final ParamType paramType;

    private final BTypeNode bType;
    private final Token ident;
    // 这两个字段只在 ARRAY 类型下有意义
    private final Token lbrack;
    private final Token rbrack;

    // 2. 为普通变量创建一个构造函数
    public FuncFParamNode(BTypeNode bType, Token ident) {
        super(bType.getLineNumber());
        this.paramType = ParamType.SCALAR;
        this.bType = bType;
        this.ident = ident;
        this.lbrack = null;
        this.rbrack = null;
    }

    // 3. 为数组变量创建另一个构造函数
    public FuncFParamNode(BTypeNode bType, Token ident, Token lbrack, Token rbrack) {
        super(bType.getLineNumber());
        this.paramType = ParamType.ARRAY;
        this.bType = bType;
        this.ident = ident;
        this.lbrack = lbrack;
        this.rbrack = rbrack;
    }

    // 4. (推荐) 添加 getter
    public ParamType getParamType() {
        return paramType;
    }
}