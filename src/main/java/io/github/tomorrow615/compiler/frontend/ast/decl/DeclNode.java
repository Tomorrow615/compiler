package io.github.tomorrow615.compiler.frontend.ast.decl;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;

public abstract class DeclNode extends ASTNode {
    protected DeclNode(int lineNumber) {
        super(lineNumber);
    }
}
