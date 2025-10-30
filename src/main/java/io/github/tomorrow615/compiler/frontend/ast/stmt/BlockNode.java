package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.BlockItemNode;
import java.util.List;

public class BlockNode extends StmtNode {
    private final List<BlockItemNode> blockItems;
    private final int endLineNumber;

    public BlockNode(List<BlockItemNode> blockItems, int lineNumber, int endLineNumber) {
        super(lineNumber);
        this.blockItems = blockItems;
        this.endLineNumber = endLineNumber;
    }

    public List<BlockItemNode> getBlockItems() {
        return blockItems;
    }

    public int getEndLineNumber() {
        return endLineNumber;
    }
}