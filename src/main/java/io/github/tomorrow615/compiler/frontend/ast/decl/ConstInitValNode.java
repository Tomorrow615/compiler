package io.github.tomorrow615.compiler.frontend.ast.decl;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.ConstExpNode;
import java.util.List;

public class ConstInitValNode extends ASTNode {

    public enum Type {
        SINGLE,
        ARRAY
    }

    private final Type type;

    private final ConstExpNode singleInit;

    private final List<ConstExpNode> arrayInit;

    public ConstInitValNode(ConstExpNode singleInit) {
        super(singleInit.getLineNumber());
        this.type = Type.SINGLE;
        this.singleInit = singleInit;
        this.arrayInit = null;
    }

    public ConstInitValNode(List<ConstExpNode> arrayInit, int lineNumber) {
        super(lineNumber);
        this.type = Type.ARRAY;
        this.singleInit = null;
        this.arrayInit = arrayInit;
    }

    public Type getType() {
        return type;
    }

    public ConstExpNode getSingleInit() {
        return singleInit;
    }

    public List<ConstExpNode> getArrayInit() {
        return arrayInit;
    }
}