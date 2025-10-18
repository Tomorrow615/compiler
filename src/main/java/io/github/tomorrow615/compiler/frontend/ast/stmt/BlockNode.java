package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.BlockItemNode;
import java.util.List;

public class BlockNode extends StmtNode {
    private final List<BlockItemNode> blockItems;

    public BlockNode(List<BlockItemNode> blockItems, int lineNumber) {
        super(lineNumber);
        this.blockItems = blockItems;
    }

    public List<BlockItemNode> getBlockItems() {
        return blockItems;
    }
}