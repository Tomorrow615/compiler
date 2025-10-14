package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.BlockItemNode;

public abstract class StmtNode extends BlockItemNode {
    protected StmtNode(int lineNumber) {
        super(lineNumber);
    }
}
