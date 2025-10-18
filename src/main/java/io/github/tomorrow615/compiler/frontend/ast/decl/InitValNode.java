package io.github.tomorrow615.compiler.frontend.ast.decl;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.ExpNode;
import java.util.List;

public class InitValNode extends ASTNode {
    public enum Type {
        SINGLE,
        ARRAY
    }

    private final Type type;

    private final ExpNode singleInit;

    private final List<ExpNode> arrayInit;

    public InitValNode(ExpNode singleInit) {
        super(singleInit.getLineNumber());
        this.type = Type.SINGLE;
        this.singleInit = singleInit;
        this.arrayInit = null;
    }

    // 构造函数 for array initializer
    public InitValNode(List<ExpNode> arrayInit, int lineNumber) {
        super(lineNumber);
        this.type = Type.ARRAY;
        this.singleInit = null;
        this.arrayInit = arrayInit;
    }

    public Type getType() {
        return type;
    }

    public ExpNode getSingleInit() {
        return singleInit;
    }

    public List<ExpNode> getArrayInit() {
        return arrayInit;
    }
}
