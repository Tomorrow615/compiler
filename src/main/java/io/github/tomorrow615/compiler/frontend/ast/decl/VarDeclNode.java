package io.github.tomorrow615.compiler.frontend.ast.decl;

public class VarDeclNode extends DeclNode {
    // 后面我们会在这里添加字段来存储 static, BType, VarDef列表, 分号等
    public VarDeclNode(int lineNumber) {
        super(lineNumber);
    }
}
