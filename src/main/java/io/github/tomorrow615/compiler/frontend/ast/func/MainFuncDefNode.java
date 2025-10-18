package io.github.tomorrow615.compiler.frontend.ast.func;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.stmt.BlockNode;

public class MainFuncDefNode extends ASTNode {
    private final BlockNode block;

    public MainFuncDefNode(BlockNode block, int lineNumber) {
        super(lineNumber);
        this.block = block;
    }

    public BlockNode getBlock() {
        return block;
    }
}
