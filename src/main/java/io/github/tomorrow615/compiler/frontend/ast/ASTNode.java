package io.github.tomorrow615.compiler.frontend.ast;

// 所有AST节点的抽象基类
public abstract class ASTNode {
    // 我们可以把节点的起始行号存起来，这在后续错误报告中非常有用
    private final int lineNumber;

    protected ASTNode(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    // 暂时先留一个抽象方法，用于第三阶段生成评测输出
    // public abstract void print(PrintWriter out);
}