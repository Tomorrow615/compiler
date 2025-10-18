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

    @Override
    public int compareTo(Error other) {
        return Integer.compare(this.lineNumber, other.lineNumber);
    }

    public String formatForOutput() {
        return this.lineNumber + " " + this.errorCode;
    }
}