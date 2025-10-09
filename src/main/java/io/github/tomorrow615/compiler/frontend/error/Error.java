package io.github.tomorrow615.compiler.frontend.error;

public class Error implements Comparable<Error> {
    private final int lineNumber;
    private final char errorCode;

    public Error(int lineNumber, char errorCode) {
        this.lineNumber = lineNumber;
        this.errorCode = errorCode;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * 实现 Comparable 接口，使得错误可以按行号排序.
     */
    @Override
    public int compareTo(Error other) {
        return Integer.compare(this.lineNumber, other.lineNumber);
    }

    /**
     * 用于格式化输出到 error.txt.
     * @return 符合评测格式的字符串.
     */
    public String formatForOutput() {
        return this.lineNumber + " " + this.errorCode;
    }
}