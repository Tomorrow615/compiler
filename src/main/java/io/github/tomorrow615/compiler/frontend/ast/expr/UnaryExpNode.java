package io.github.tomorrow615.compiler.frontend.ast.expr;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;

public class UnaryExpNode extends ASTNode {
    public enum Type {
        PRIMARY, FUNC_CALL, UNARY_OP
    }
    private final Type type;
    private final PrimaryExpNode primaryExp;
    private final Token ident;
    private final FuncRParamsNode funcRParams; // for func call
    private final UnaryOpNode unaryOp;
    private final UnaryExpNode unaryExp; // for unary op

    public UnaryExpNode(PrimaryExpNode primaryExp) {
        super(primaryExp.getLineNumber());
        this.type = Type.PRIMARY;
        this.primaryExp = primaryExp;
        this.ident = null;
        this.funcRParams = null;
        this.unaryOp = null;
        this.unaryExp = null;
    }

    public UnaryExpNode(Token ident, FuncRParamsNode funcRParams) {
        super(ident.getLineNumber());
        this.type = Type.FUNC_CALL;
        this.primaryExp = null;
        this.ident = ident;
        this.funcRParams = funcRParams;
        this.unaryOp = null;
        this.unaryExp = null;
    }

    public UnaryExpNode(UnaryOpNode unaryOp, UnaryExpNode unaryExp) {
        super(unaryOp.getLineNumber());
        this.type = Type.UNARY_OP;
        this.primaryExp = null;
        this.ident = null;
        this.funcRParams = null;
        this.unaryOp = unaryOp;
        this.unaryExp = unaryExp;
    }

    public Type getType() {
        return type;
    }

    public PrimaryExpNode getPrimaryExp() {
        return primaryExp;
    }

    public Token getIdent() {
        return ident;
    }

    public FuncRParamsNode getFuncRParams() {
        return funcRParams;
    }

    public UnaryOpNode getUnaryOp() {
        return unaryOp;
    }

    public UnaryExpNode getUnaryExp() {
        return unaryExp;
    }
}
