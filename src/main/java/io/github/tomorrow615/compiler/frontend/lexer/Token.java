package io.github.tomorrow615.compiler.frontend.lexer;

public class Token {
    private final TokenType type;
    private final String text;
    private final Object value;
    private final int lineNumber;

    public Token(TokenType type, String text, Object value, int lineNumber) {
        this.type = type;
        this.text = text;
        this.value = value;
        this.lineNumber = lineNumber;
    }

    public TokenType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * 此方法用于格式化输出到 lexer.txt.
     * @return 符合评测格式的字符串.
     */
    public String formatForOutput() {
        return this.type.name() + " " + this.text;
    }
}