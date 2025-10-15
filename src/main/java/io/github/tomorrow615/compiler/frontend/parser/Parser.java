package io.github.tomorrow615.compiler.frontend.parser;

import io.github.tomorrow615.compiler.frontend.ast.CompUnitNode;
import io.github.tomorrow615.compiler.frontend.ast.decl.*;
import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.ast.func.*;
import io.github.tomorrow615.compiler.frontend.ast.stmt.BlockNode;
import io.github.tomorrow615.compiler.frontend.error.ErrorReporter;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import io.github.tomorrow615.compiler.frontend.lexer.TokenType;
import io.github.tomorrow615.compiler.util.ParserRecorder;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    // --- 核心工具 ---
    private final List<Token> tokens;
    private int currentPos = 0;
    private final ParserRecorder recorder;
    private Token lastConsumedToken = null;

    // --- 子解析器 ---
    private final ExpressionParser expressionParser;
    private final StatementParser statementParser;

    // --- 构造函数 ---
    public Parser(List<Token> tokens, ParserRecorder recorder) {
        this.tokens = tokens;
        this.recorder = recorder;
        this.expressionParser = new ExpressionParser(this);
        this.statementParser = new StatementParser(this, this.expressionParser);
    }

    // --- 公共工具方法 (供子解析器调用) ---
    Token peek() {
        if (currentPos < tokens.size()) {
            return tokens.get(currentPos);
        }
        return new Token(TokenType.EOF, "EOF", null, -1);
    }

    Token peekNext() {
        if (currentPos + 1 < tokens.size()) {
            return tokens.get(currentPos + 1);
        }
        return new Token(TokenType.EOF, "EOF", null, -1);
    }

    Token peek(int k) {
        if (currentPos + k < tokens.size()) {
            return tokens.get(currentPos + k);
        }
        return new Token(TokenType.EOF, "EOF", null, -1);
    }

    Token consume() {
        Token token = advance();
        this.lastConsumedToken = token;
        if (recorder != null) {
            recorder.recordToken(token);
        }
        return token;
    }

    private Token advance() {
        if (currentPos < tokens.size()) {
            return tokens.get(currentPos++);
        }
        return new Token(TokenType.EOF, "EOF", null, -1);
    }

    Token matchAndConsume(TokenType expectedType, char errorCode) {
        if (peek().getType() == expectedType) {
            return consume();
        } else {
            int line = (lastConsumedToken != null) ? lastConsumedToken.getLineNumber() : peek().getLineNumber();
            ErrorReporter.addError(line, errorCode);
            return new Token(expectedType, "", null, line); // 返回一个哨兵Token避免空指针
        }
    }

    int savePosition() {
        return this.currentPos;
    }

    void restorePosition(int position) {
        this.currentPos = position;
    }

    ParserRecorder getRecorder() {
        return this.recorder;
    }

    public List<Token> getTokens() {
        return this.tokens;
    }

    // --- 主解析入口 ---
    public CompUnitNode parse() {
        return parseCompUnit();
    }

    // --- 顶层和声明解析 (保留在主Parser中) ---

    private CompUnitNode parseCompUnit() {
        List<DeclNode> decls = new ArrayList<>();
        List<FuncDefNode> funcDefs = new ArrayList<>();
        MainFuncDefNode mainFuncDef;
        int startLine = peek().getLineNumber();

        while (peek().getType() == TokenType.CONSTTK ||
                (peek().getType() == TokenType.INTTK && peek(2).getType() != TokenType.LPARENT)) {
            decls.add(parseDecl());
        }

        while (peek().getType() == TokenType.VOIDTK ||
                (peek().getType() == TokenType.INTTK && !peekNext().getText().equals("main"))) {
            funcDefs.add(parseFuncDef());
        }

        mainFuncDef = parseMainFuncDef();

        recorder.recordSyntax("CompUnit");
        return new CompUnitNode(decls, funcDefs, mainFuncDef, startLine);
    }

    public DeclNode parseDecl() {
        if (peek().getType() == TokenType.CONSTTK) {
            return parseConstDecl();
        } else {
            return parseVarDecl();
        }
    }

    private ConstDeclNode parseConstDecl() {
        Token constToken = matchAndConsume(TokenType.CONSTTK, 'z');
        int startLine = (constToken != null) ? constToken.getLineNumber() : (lastConsumedToken != null ? lastConsumedToken.getLineNumber() : -1);

        BTypeNode bType = parseBType();
        List<ConstDefNode> constDefs = new ArrayList<>();
        constDefs.add(parseConstDef());

        while (peek().getType() == TokenType.COMMA) {
            consume();
            constDefs.add(parseConstDef());
        }

        Token semicn = matchAndConsume(TokenType.SEMICN, 'i');
        recorder.recordSyntax("ConstDecl");
        return new ConstDeclNode(constToken, bType, constDefs, semicn, startLine);
    }

    private BTypeNode parseBType() {
        Token typeToken = matchAndConsume(TokenType.INTTK, 'z');
        return new BTypeNode(typeToken);
    }

    private ConstDefNode parseConstDef() {
        Token ident = matchAndConsume(TokenType.IDENFR, 'z');
        List<Token> lBracks = new ArrayList<>();
        List<ConstExpNode> constExps = new ArrayList<>();
        List<Token> rBracks = new ArrayList<>();

        while (peek().getType() == TokenType.LBRACK) {
            lBracks.add(matchAndConsume(TokenType.LBRACK, 'z'));
            constExps.add(expressionParser.parseConstExp()); // <-- 修正后的委托调用
            rBracks.add(matchAndConsume(TokenType.RBRACK, 'k'));
        }

        Token assignToken = matchAndConsume(TokenType.ASSIGN, 'z');
        ConstInitValNode constInitVal = parseConstInitVal();
        recorder.recordSyntax("ConstDef");
        return new ConstDefNode(ident, lBracks, constExps, rBracks, assignToken, constInitVal);
    }

    private ConstInitValNode parseConstInitVal() {
        if (peek().getType() == TokenType.LBRACE) {
            Token lBrace = consume();
            List<ConstExpNode> arrayInit = new ArrayList<>();
            List<Token> commas = new ArrayList<>();

            if (peek().getType() != TokenType.RBRACE) {
                arrayInit.add(expressionParser.parseConstExp()); // <-- 修正后的委托调用
                while (peek().getType() == TokenType.COMMA) {
                    commas.add(consume());
                    arrayInit.add(expressionParser.parseConstExp()); // <-- 修正后的委托调用
                }
            }
            Token rBrace = matchAndConsume(TokenType.RBRACE, 'z');
            recorder.recordSyntax("ConstInitVal");
            return new ConstInitValNode(lBrace, arrayInit, commas, rBrace);
        } else {
            ConstExpNode singleInit = expressionParser.parseConstExp(); // <-- 修正后的委托调用
            recorder.recordSyntax("ConstInitVal");
            return new ConstInitValNode(singleInit);
        }
    }

    private VarDeclNode parseVarDecl() {
        int startLine = peek().getLineNumber();
        Token staticToken = null;
        if (peek().getType() == TokenType.STATICTK) {
            staticToken = consume();
        }

        BTypeNode bType = parseBType();
        List<VarDefNode> varDefs = new ArrayList<>();
        varDefs.add(parseVarDef());

        while (peek().getType() == TokenType.COMMA) {
            consume();
            varDefs.add(parseVarDef());
        }

        Token semicn = matchAndConsume(TokenType.SEMICN, 'i');
        recorder.recordSyntax("VarDecl");
        return new VarDeclNode(staticToken, bType, varDefs, semicn, startLine);
    }

    private VarDefNode parseVarDef() {
        Token ident = matchAndConsume(TokenType.IDENFR, 'z');
        List<Token> lBracks = new ArrayList<>();
        List<ConstExpNode> constExps = new ArrayList<>();
        List<Token> rBracks = new ArrayList<>();

        while (peek().getType() == TokenType.LBRACK) {
            lBracks.add(matchAndConsume(TokenType.LBRACK, 'z'));
            constExps.add(expressionParser.parseConstExp()); // <-- 修正后的委托调用
            rBracks.add(matchAndConsume(TokenType.RBRACK, 'k'));
        }

        Token assignToken = null;
        InitValNode initVal = null;
        if (peek().getType() == TokenType.ASSIGN) {
            assignToken = matchAndConsume(TokenType.ASSIGN, 'z');
            initVal = parseInitVal();
        }
        recorder.recordSyntax("VarDef");
        return new VarDefNode(ident, lBracks, constExps, rBracks, assignToken, initVal);
    }

    private InitValNode parseInitVal() {
        if (peek().getType() == TokenType.LBRACE) {
            Token lBrace = consume();
            List<ExpNode> arrayInit = new ArrayList<>();
            List<Token> commas = new ArrayList<>();

            if (peek().getType() != TokenType.RBRACE) {
                arrayInit.add(expressionParser.parseExp()); // <-- 修正后的委托调用
                while (peek().getType() == TokenType.COMMA) {
                    commas.add(consume());
                    arrayInit.add(expressionParser.parseExp()); // <-- 修正后的委托调用
                }
            }
            Token rBrace = matchAndConsume(TokenType.RBRACE, 'z');
            recorder.recordSyntax("InitVal");
            return new InitValNode(lBrace, arrayInit, commas, rBrace);
        } else {
            ExpNode singleInit = expressionParser.parseExp(); // <-- 修正后的委托调用
            recorder.recordSyntax("InitVal");
            return new InitValNode(singleInit);
        }
    }

    private FuncDefNode parseFuncDef() {
        FuncTypeNode funcType = parseFuncType();
        Token ident = consume();
        Token lparen = consume();

        FuncFParamsNode funcFParams = null;
        if (peek().getType() != TokenType.RPARENT && peek().getType() != TokenType.EOF) {
            funcFParams = parseFuncFParams();
        }

        Token rparen = matchAndConsume(TokenType.RPARENT, 'j');
        BlockNode block = statementParser.parseBlock(); // <-- 委托调用
        recorder.recordSyntax("FuncDef");
        return new FuncDefNode(funcType, ident, lparen, funcFParams, rparen, block);
    }

    private FuncTypeNode parseFuncType() {
        Token typeToken = consume();
        recorder.recordSyntax("FuncType");
        return new FuncTypeNode(typeToken);
    }

    private FuncFParamsNode parseFuncFParams() {
        List<FuncFParamNode> params = new ArrayList<>();
        List<Token> commas = new ArrayList<>();
        params.add(parseFuncFParam());

        while (peek().getType() == TokenType.COMMA) {
            commas.add(consume());
            params.add(parseFuncFParam());
        }

        recorder.recordSyntax("FuncFParams");
        return new FuncFParamsNode(params, commas);
    }

    private FuncFParamNode parseFuncFParam() {
        BTypeNode bType = parseBType();
        Token ident = matchAndConsume(TokenType.IDENFR, 'z');
        Token lbrack = null;
        Token rbrack = null;

        if (peek().getType() == TokenType.LBRACK) {
            lbrack = matchAndConsume(TokenType.LBRACK, 'z');
            rbrack = matchAndConsume(TokenType.RBRACK, 'k');
        }

        recorder.recordSyntax("FuncFParam");
        return new FuncFParamNode(bType, ident, lbrack, rbrack);
    }

    private MainFuncDefNode parseMainFuncDef() {
        Token intToken = matchAndConsume(TokenType.INTTK, 'z');
        Token mainToken = matchAndConsume(TokenType.MAINTK, 'z');
        Token lparen = matchAndConsume(TokenType.LPARENT, 'j');
        Token rparen = matchAndConsume(TokenType.RPARENT, 'j');
        BlockNode block = statementParser.parseBlock(); // <-- 委托调用
        recorder.recordSyntax("MainFuncDef");
        return new MainFuncDefNode(intToken, mainToken, lparen, rparen, block);
    }

    public LValNode parseLVal(boolean isSpeculative) {
        Token ident = matchAndConsume(TokenType.IDENFR, 'z');
        List<Token> lBracks = new ArrayList<>();
        List<ExpNode> arrayExps = new ArrayList<>();
        List<Token> rBracks = new ArrayList<>();

        while (peek().getType() == TokenType.LBRACK) {
            lBracks.add(matchAndConsume(TokenType.LBRACK, 'z'));
            arrayExps.add(expressionParser.parseExp()); // <-- 修正后的委托调用
            rBracks.add(matchAndConsume(TokenType.RBRACK, 'k'));
        }

        if (!isSpeculative) {
            recorder.recordSyntax("LVal");
        }
        return new LValNode(ident, lBracks, arrayExps, rBracks);
    }
}