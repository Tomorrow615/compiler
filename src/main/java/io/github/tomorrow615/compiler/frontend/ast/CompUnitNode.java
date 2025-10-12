package io.github.tomorrow615.compiler.frontend.ast;

import io.github.tomorrow615.compiler.frontend.ast.decl.DeclNode;
import io.github.tomorrow615.compiler.frontend.ast.func.FuncDefNode;
import io.github.tomorrow615.compiler.frontend.ast.func.MainFuncDefNode;

import java.util.List;

public class CompUnitNode extends ASTNode {
    private final List<DeclNode> decls;
    private final List<FuncDefNode> funcDefs;
    private final MainFuncDefNode mainFuncDef;

    public CompUnitNode(List<DeclNode> decls, List<FuncDefNode> funcDefs, MainFuncDefNode mainFuncDef, int lineNumber) {
        super(lineNumber);
        this.decls = decls;
        this.funcDefs = funcDefs;
        this.mainFuncDef = mainFuncDef;
    }
}