package io.github.tomorrow615.compiler.frontend.ast.stmt;

import io.github.tomorrow615.compiler.frontend.ast.BlockItemNode;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.util.List;

// 1. Block 是一种语句，所以继承 StmtNode
public class BlockNode extends StmtNode {
    private final Token lBrace;
    private final List<BlockItemNode> blockItems; // 2. 替换掉 TODO，用 List 存储
    private final Token rBrace;

    // 3. 更新构造函数
    public BlockNode(Token lBrace, List<BlockItemNode> blockItems, Token rBrace) {
        super(lBrace.getLineNumber()); // 4. 调用父类构造函数
        this.lBrace = lBrace;
        this.blockItems = blockItems;
        this.rBrace = rBrace;
    }
}