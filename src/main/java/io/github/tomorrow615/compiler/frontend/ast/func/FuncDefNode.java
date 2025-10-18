package io.github.tomorrow615.compiler.frontend.ast.func;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.stmt.BlockNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class FuncDefNode extends ASTNode {
    private final FuncTypeNode funcType;
    private final Token ident;
    private final List<FuncFParamNode> funcFParams;
    private final BlockNode block;

    public FuncDefNode(FuncTypeNode funcType, Token ident, List<FuncFParamNode> funcFParams, BlockNode block) {
        super(funcType.getLineNumber());
        this.funcType = funcType;
        this.ident = ident;
        this.funcFParams = funcFParams;
        this.block = block;
    }

    public FuncTypeNode getFuncType() {
        return funcType;
    }

    public Token getIdent() {
        return ident;
    }
    public List<FuncFParamNode> getFuncFParams() {
        return funcFParams;
    }

    public BlockNode getBlock() {
        return block;
    }
}
