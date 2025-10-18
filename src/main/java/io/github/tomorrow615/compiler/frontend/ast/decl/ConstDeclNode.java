package io.github.tomorrow615.compiler.frontend.ast.decl;

import java.util.List;

public class ConstDeclNode extends DeclNode {
    private final BTypeNode bType;
    private final List<ConstDefNode> constDefs;

    public ConstDeclNode(BTypeNode bType, List<ConstDefNode> constDefs) {
        super(bType.getLineNumber());
        this.bType = bType;
        this.constDefs = constDefs;
    }

    public BTypeNode getbType() {
        return bType;
    }

    public List<ConstDefNode> getConstDefs() {
        return constDefs;
    }
}