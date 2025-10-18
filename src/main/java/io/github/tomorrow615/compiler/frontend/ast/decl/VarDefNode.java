package io.github.tomorrow615.compiler.frontend.ast.decl;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.ConstExpNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

public class VarDefNode extends ASTNode {
    public enum Type {
        UNINITIALIZED, // 无初始值
        INITIALIZED    // 有初始值
    }

    private final Type type;
    private final Token ident;
    private final List<ConstExpNode> constExps;
    private final InitValNode initVal;

    public VarDefNode(Token ident, List<ConstExpNode> constExps) {
        super(ident.getLineNumber());
        this.type = Type.UNINITIALIZED;
        this.ident = ident;
        this.constExps = constExps;
        this.initVal = null;
    }

    public VarDefNode(Token ident, List<ConstExpNode> constExps, InitValNode initVal) {
        super(ident.getLineNumber());
        this.type = Type.INITIALIZED;
        this.ident = ident;
        this.constExps = constExps;
        this.initVal = initVal;
    }

    public Type getType() {
        return type;
    }

    public Token getIdent() {
        return ident;
    }

    public List<ConstExpNode> getConstExps() {
        return constExps;
    }

    public InitValNode getInitVal() {
        return initVal;

    }
    public int getDimension() {
        return constExps.size();
    }
}