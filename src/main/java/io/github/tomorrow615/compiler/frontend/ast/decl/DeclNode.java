package io.github.tomorrow615.compiler.frontend.ast.decl;

import io.github.tomorrow615.compiler.frontend.ast.BlockItemNode;

public abstract class DeclNode extends BlockItemNode {
    protected DeclNode(int lineNumber) {
        super(lineNumber);
    }
}