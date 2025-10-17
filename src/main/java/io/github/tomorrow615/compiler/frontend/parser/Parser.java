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
    private final List<Token> tokens;
    private int currentPos = 0;
    private final ParserRecorder recorder;
    private Token lastConsumedToken = null;

    private final ExpressionParser expressionParser;
    private final StatementParser statementParser;

    public Parser(List<Token> tokens, ParserRecorder recorder) {
        this.tokens = tokens;
        this.recorder = recorder;
        this.expressionParser = new ExpressionParser(this);
        this.statementParser = new StatementParser(this, this.expressionParser);
    }

    private Token advance() {
        if (currentPos < tokens.size()) {
            return tokens.get(currentPos++);
        }
        return new Token(TokenType.EOF, "EOF", null, -1);
    }

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

    public CompUnitNode parse() {
        return parseCompUnit();
    }

    // 编译单元 CompUnit → {Decl} {FuncDef} MainFuncDef
    private CompUnitNode parseCompUnit() {
        List<DeclNode> decls = new ArrayList<>();
        List<FuncDefNode> funcDefs = new ArrayList<>();
        MainFuncDefNode mainFuncDef;
        int startLine = peek().getLineNumber();

        while (peek().getType() == TokenType.CONSTTK ||
                peek().getType() == TokenType.STATICTK ||
                (peek().getType() == TokenType.INTTK && peek(2).getType() != TokenType.LPARENT)){
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

    // 声明 Decl → ConstDecl | VarDecl
    public DeclNode parseDecl() {
        if (peek().getType() == TokenType.CONSTTK) {
            return parseConstDecl();
        } else {
            return parseVarDecl();
        }
    }

    // 常量声明 ConstDecl → 'const' BType ConstDef { ',' ConstDef } ';' // i
    private ConstDeclNode parseConstDecl() {
        Token constToken = consume();
        BTypeNode bType = parseBType();
        List<ConstDefNode> constDefs = new ArrayList<>();
        constDefs.add(parseConstDef());

        while (peek().getType() == TokenType.COMMA) {
            consume();
            constDefs.add(parseConstDef());
        }

        Token semicn = matchAndConsume(TokenType.SEMICN, 'i');
        recorder.recordSyntax("ConstDecl");
        return new ConstDeclNode(constToken, bType, constDefs, semicn);
    }

    // 基本类型 BType → 'int'
    private BTypeNode parseBType() {
        Token typeToken = consume();
        recorder.recordSyntax("BType");
        return new BTypeNode(typeToken);
    }

    // 常量定义 ConstDef → Ident [ '[' ConstExp ']' ] '=' ConstInitVal // k
    // 这里支持多维数组，但 ConstInitVal 还需做对应调整
    private ConstDefNode parseConstDef() {
        Token ident = consume();
        List<Token> lBracks = new ArrayList<>();
        List<ConstExpNode> constExps = new ArrayList<>();
        List<Token> rBracks = new ArrayList<>();

        while (peek().getType() == TokenType.LBRACK) {
            lBracks.add(consume());
            constExps.add(expressionParser.parseConstExp());
            rBracks.add(matchAndConsume(TokenType.RBRACK, 'k'));
        }

        Token assignToken = consume();
        ConstInitValNode constInitVal = parseConstInitVal();
        recorder.recordSyntax("ConstDef");
        return new ConstDefNode(ident, lBracks, constExps, rBracks, assignToken, constInitVal);
    }

    // 常量初值 ConstInitVal → ConstExp | '{' [ ConstExp { ',' ConstExp } ] '}'
    private ConstInitValNode parseConstInitVal() {
        if (peek().getType() == TokenType.LBRACE) {
            Token lBrace = consume();
            List<ConstExpNode> arrayInit = new ArrayList<>();
            List<Token> commas = new ArrayList<>();

            if (peek().getType() != TokenType.RBRACE) {
                arrayInit.add(expressionParser.parseConstExp());
                while (peek().getType() == TokenType.COMMA) {
                    commas.add(consume());
                    arrayInit.add(expressionParser.parseConstExp());
                }
            }
            Token rBrace = consume();
            recorder.recordSyntax("ConstInitVal");
            return new ConstInitValNode(lBrace, arrayInit, commas, rBrace);
        } else {
            ConstExpNode singleInit = expressionParser.parseConstExp();
            recorder.recordSyntax("ConstInitVal");
            return new ConstInitValNode(singleInit);
        }
    }

    // 变量声明 VarDecl → [ 'static' ] BType VarDef { ',' VarDef } ';' // i
    private VarDeclNode parseVarDecl() {
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
        return new VarDeclNode(staticToken, bType, varDefs, semicn);
    }

    // 变量定义 VarDef → Ident [ '[' ConstExp ']' ] | Ident [ '[' ConstExp ']' ] '=' InitVal // k
    // 同样支持多维数组，但对应的 InitVal 还需做对应调整
    private VarDefNode parseVarDef() {
        Token ident = consume();
        List<Token> lBracks = new ArrayList<>();
        List<ConstExpNode> constExps = new ArrayList<>();
        List<Token> rBracks = new ArrayList<>();

        while (peek().getType() == TokenType.LBRACK) {
            lBracks.add(consume());
            constExps.add(expressionParser.parseConstExp());
            rBracks.add(matchAndConsume(TokenType.RBRACK, 'k'));
        }

        if (peek().getType() == TokenType.ASSIGN) {
            Token assignToken = consume();
            InitValNode initVal = parseInitVal();
            recorder.recordSyntax("VarDef");
            return new VarDefNode(ident, lBracks, constExps, rBracks, assignToken, initVal);
        } else {
            recorder.recordSyntax("VarDef");
            return new VarDefNode(ident, lBracks, constExps, rBracks);
        }
    }

    // 变量初值 InitVal → Exp | '{' [ Exp { ',' Exp } ] '}'
    private InitValNode parseInitVal() {
        if (peek().getType() == TokenType.LBRACE) {
            Token lBrace = consume();
            List<ExpNode> arrayInit = new ArrayList<>();
            List<Token> commas = new ArrayList<>();

            if (peek().getType() != TokenType.RBRACE) {
                arrayInit.add(expressionParser.parseExp());
                while (peek().getType() == TokenType.COMMA) {
                    commas.add(consume());
                    arrayInit.add(expressionParser.parseExp());
                }
            }
            Token rBrace = consume();
            recorder.recordSyntax("InitVal");
            return new InitValNode(lBrace, arrayInit, commas, rBrace);
        } else {
            ExpNode singleInit = expressionParser.parseExp();
            recorder.recordSyntax("InitVal");
            return new InitValNode(singleInit);
        }
    }

    // 函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block // j
    private FuncDefNode parseFuncDef() {
        FuncTypeNode funcType = parseFuncType();
        Token ident = consume();
        Token lparen = consume();

        FuncFParamsNode funcFParams = null;
        if (peek().getType() != TokenType.RPARENT) {
            funcFParams = parseFuncFParams();
        }

        Token rparen = matchAndConsume(TokenType.RPARENT, 'j');
        BlockNode block = statementParser.parseBlock();
        recorder.recordSyntax("FuncDef");
        return new FuncDefNode(funcType, ident, lparen, funcFParams, rparen, block);
    }

    // 函数类型 FuncType → 'void' | 'int'
    private FuncTypeNode parseFuncType() {
        Token typeToken = consume();
        recorder.recordSyntax("FuncType");
        return new FuncTypeNode(typeToken);
    }

    // 函数形参表 FuncFParams → FuncFParam { ',' FuncFParam }
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

    // 函数形参 FuncFParam → BType Ident ['[' ']'] // k
    private FuncFParamNode parseFuncFParam() {
        BTypeNode bType = parseBType();
        Token ident = consume();

        if (peek().getType() == TokenType.LBRACK) {
            Token lbrack = consume();
            Token rbrack = matchAndConsume(TokenType.RBRACK, 'k');
            recorder.recordSyntax("FuncFParam");
            return new FuncFParamNode(bType, ident, lbrack, rbrack);
        } else {
            recorder.recordSyntax("FuncFParam");
            return new FuncFParamNode(bType, ident);
        }
    }

    // 主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block // j
    private MainFuncDefNode parseMainFuncDef() {
        Token intToken = consume();
        Token mainToken = consume();
        Token lparen = consume();
        Token rparen = matchAndConsume(TokenType.RPARENT, 'j');
        BlockNode block = statementParser.parseBlock();
        recorder.recordSyntax("MainFuncDef");
        return new MainFuncDefNode(intToken, mainToken, lparen, rparen, block);
    }

    // 左值表达式 LVal → Ident ['[' Exp ']'] // k
    // 可以支持 a[i][j][k]
    public LValNode parseLVal(boolean isSpeculative) {
        Token ident = consume();

        if (peek().getType() == TokenType.LBRACK) {
            // 是数组元素
            List<Token> lBracks = new ArrayList<>();
            List<ExpNode> arrayExps = new ArrayList<>();
            List<Token> rBracks = new ArrayList<>();

            while (peek().getType() == TokenType.LBRACK) {
                lBracks.add(consume());
                arrayExps.add(expressionParser.parseExp());
                rBracks.add(matchAndConsume(TokenType.RBRACK, 'k'));
            }

            if (!isSpeculative) {
                recorder.recordSyntax("LVal");
            }
            return new LValNode(ident, lBracks, arrayExps, rBracks);
        } else {
            // 是普通变量
            if (!isSpeculative) {
                recorder.recordSyntax("LVal");
            }
            return new LValNode(ident);
        }
    }
}