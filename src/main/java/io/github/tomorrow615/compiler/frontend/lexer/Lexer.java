package io.github.tomorrow615.compiler.frontend.lexer;

import io.github.tomorrow615.compiler.frontend.error.ErrorReporter;
import io.github.tomorrow615.compiler.util.*;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {
    private final PushbackReader reader;
    private final StringBuilder tokenText = new StringBuilder();
    private int currentLine = 1;
    private final LexerRecorder recorder;

    private static final Map<String, TokenType> keywords = new HashMap<>();

    static {
        keywords.put("main", TokenType.MAINTK);
        keywords.put("const", TokenType.CONSTTK);
        keywords.put("int", TokenType.INTTK);
        keywords.put("break", TokenType.BREAKTK);
        keywords.put("continue", TokenType.CONTINUETK);
        keywords.put("if", TokenType.IFTK);
        keywords.put("else", TokenType.ELSETK);
        keywords.put("for", TokenType.FORTK);
        keywords.put("printf", TokenType.PRINTFTK);
        keywords.put("return", TokenType.RETURNTK);
        keywords.put("void", TokenType.VOIDTK);
        keywords.put("static", TokenType.STATICTK);
    }

    public Lexer(String sourceCode, LexerRecorder recorder) {
        this.reader = new PushbackReader(new StringReader(sourceCode));
        this.recorder = recorder;
    }

    public List<Token> getAllTokens() {
        List<Token> tokens = new ArrayList<>();
        Token token;
        do {
            token = getNextToken();
            recorder.recordToken(token);
            tokens.add(token);
        } while (token.getType() != TokenType.EOF);
        return tokens;
    }

    private Token getNextToken() {
        int c = readChar();

        while (Character.isWhitespace(c)) {
            if (c == '\n') {
                currentLine++;
            }
            c = readChar();
        }

        if (c == -1) {
            return new Token(TokenType.EOF, "EOF", null, currentLine);
        }

        unreadChar(c); // 把读到的第一个有效字符退回去

        if (c == '/') {
            return handleCommentOrDivision();
        } else if (Character.isLetter(c) || c == '_') {
            return handleIdentifierOrKeyword();
        } else if (Character.isDigit(c)) {
            return handleNumber();
        } else if (c == '"') {
            return handleStringLiteral();
        } else {
            return handleSymbol();
        }
    }

    private Token handleCommentOrDivision() {
        readChar();
        int nextChar = peekChar();

        if (nextChar == '/') {
            readChar();
            while (peekChar() != '\n' && peekChar() != -1) {
                readChar();
            }
            return getNextToken();
        } else if (nextChar == '*') {
            readChar();
            while (true) {
                int c = readChar();
                if (c == -1) break;
                if (c == '\n') currentLine++;
                if (c == '*' && peekChar() == '/') {
                    readChar();
                    break;
                }
            }
            return getNextToken();
        } else {
            return new Token(TokenType.DIV, "/", null, currentLine);
        }
    }

    private Token handleIdentifierOrKeyword() {
        tokenText.setLength(0);
        tokenText.append((char) readChar());

        while (true) {
            int nextChar = peekChar();
            if (Character.isLetterOrDigit(nextChar) || nextChar == '_') {
                tokenText.append((char) readChar());
            } else {
                break;
            }
        }

        String text = tokenText.toString();
        TokenType type = keywords.getOrDefault(text, TokenType.IDENFR);
        return new Token(type, text, null, currentLine);
    }

    private Token handleNumber() {
        tokenText.setLength(0);
        tokenText.append((char) readChar());

        while (Character.isDigit(peekChar())) {
            tokenText.append((char) readChar());
        }

        String text = tokenText.toString();
        Integer value = Integer.parseInt(text);
        return new Token(TokenType.INTCON, text, value, currentLine);
    }

    private Token handleStringLiteral() {
        tokenText.setLength(0);
        tokenText.append((char) readChar());
        StringBuilder valueBuilder = new StringBuilder();

        while (true) {
            int c = readChar();
            if (c == '"') {
                tokenText.append('"');
                break;
            }
            if (c == -1) break;

            if (c == '\\') {
                if (peekChar() == 'n') {
                    readChar();
                    tokenText.append("\\n");
                    valueBuilder.append('\n');
                } else {
                    tokenText.append('\\');
                    valueBuilder.append('\\');
                }
            } else {
                tokenText.append((char) c);
                valueBuilder.append((char) c);
            }
        }

        return new Token(TokenType.STRCON, tokenText.toString(), valueBuilder.toString(), currentLine);
    }

    private Token handleSymbol() {
        tokenText.setLength(0);
        int c = readChar();
        tokenText.append((char) c);

        switch (c) {
            case '+': return new Token(TokenType.PLUS, "+", null, currentLine);
            case '-': return new Token(TokenType.MINU, "-", null, currentLine);
            case '*': return new Token(TokenType.MULT, "*", null, currentLine);
            case '%': return new Token(TokenType.MOD, "%", null, currentLine);
            case '(': return new Token(TokenType.LPARENT, "(", null, currentLine);
            case ')': return new Token(TokenType.RPARENT, ")", null, currentLine);
            case '[': return new Token(TokenType.LBRACK, "[", null, currentLine);
            case ']': return new Token(TokenType.RBRACK, "]", null, currentLine);
            case '{': return new Token(TokenType.LBRACE, "{", null, currentLine);
            case '}': return new Token(TokenType.RBRACE, "}", null, currentLine);
            case ',': return new Token(TokenType.COMMA, ",", null, currentLine);
            case ';': return new Token(TokenType.SEMICN, ";", null, currentLine);
            case '!':
                if (peekChar() == '=') {
                    tokenText.append((char)readChar());
                    return new Token(TokenType.NEQ, tokenText.toString(), null, currentLine);
                }
                return new Token(TokenType.NOT, tokenText.toString(), null, currentLine);
            case '<':
                if (peekChar() == '=') {
                    tokenText.append((char)readChar());
                    return new Token(TokenType.LEQ, tokenText.toString(), null, currentLine);
                }
                return new Token(TokenType.LSS, tokenText.toString(), null, currentLine);
            case '>':
                if (peekChar() == '=') {
                    tokenText.append((char)readChar());
                    return new Token(TokenType.GEQ, tokenText.toString(), null, currentLine);
                }
                return new Token(TokenType.GRE, tokenText.toString(), null, currentLine);
            case '=':
                if (peekChar() == '=') {
                    tokenText.append((char)readChar());
                    return new Token(TokenType.EQL, tokenText.toString(), null, currentLine);
                }
                return new Token(TokenType.ASSIGN, tokenText.toString(), null, currentLine);
            case '&':
                if (peekChar() == '&') {
                    // 这是正确的 "&&"
                    readChar(); // 消耗第二个 '&'
                    tokenText.append('&');
                    return new Token(TokenType.AND, tokenText.toString(), null, currentLine);
                } else {
                    // 这是错误的单个 '&'
                    ErrorReporter.addError(currentLine, 'a'); // 报告 a 类错误
                    return new Token(TokenType.AND, tokenText.toString(), null, currentLine);
                }
            case '|':
                if (peekChar() == '|') {
                    // 这是正确的 "||"
                    readChar(); // 消耗第二个 '|'
                    tokenText.append('|');
                    return new Token(TokenType.OR, tokenText.toString(), null, currentLine);
                } else {
                    // 这是错误的单个 '|'
                    ErrorReporter.addError(currentLine, 'a'); // 报告 a 类错误
                    return new Token(TokenType.OR, tokenText.toString(), null, currentLine);
                }
            default:
                return getNextToken();
        }
    }

    private int readChar() {
        try {
            return reader.read();
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    private void unreadChar(int c) {
        if (c != -1) {
            try {
                reader.unread(c);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private int peekChar() {
        int c = readChar();
        unreadChar(c);
        return c;
    }
}