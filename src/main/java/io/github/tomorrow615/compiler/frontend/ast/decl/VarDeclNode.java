package io.github.tomorrow615.compiler.frontend.ast.decl;

import java.util.List;

public class VarDeclNode extends DeclNode {
    private final boolean isStatic;
    private final BTypeNode bType;
    private final List<VarDefNode> varDefs;

    public VarDeclNode(boolean isStatic, BTypeNode bType, List<VarDefNode> varDefs, int lineNumber) {
        super(lineNumber);
        this.isStatic = isStatic;
        this.bType = bType;
        this.varDefs = varDefs;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public BTypeNode getbType() {
        return bType;
    }

    public List<VarDefNode> getVarDefs() {
        return varDefs;
    }
}
