package io.github.tomorrow615.compiler.frontend.ast;

public abstract class ASTNode {
    private final int lineNumber;

    protected ASTNode(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}