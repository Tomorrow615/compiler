package io.github.tomorrow615.compiler.frontend.parser;

import io.github.tomorrow615.compiler.frontend.ast.*;
import io.github.tomorrow615.compiler.frontend.ast.decl.*;
import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.ast.func.*;
import io.github.tomorrow615.compiler.frontend.ast.stmt.*;
import io.github.tomorrow615.compiler.frontend.error.*;
import io.github.tomorrow615.compiler.frontend.lexer.*;
import io.github.tomorrow615.compiler.util.*;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int currentPos = 0;
    private final ParserRecorder recorder;
    private Token lastConsumedToken = null;

    public Parser(List<Token> tokens, ParserRecorder recorder) {
        this.tokens = tokens;
        this.recorder = recorder;
    }

    private Token peek() {
        if (currentPos < tokens.size()) {
            return tokens.get(currentPos);
        }
        return new Token(TokenType.EOF, "EOF", -1, -1);
    }

    private Token peekNext() {
        if (currentPos + 1 < tokens.size()) {
            return tokens.get(currentPos + 1);
        }
        return new Token(TokenType.EOF, "EOF", -1, -1);
    }

    private Token advance() {
        if (currentPos < tokens.size()) {
            Token token = tokens.get(currentPos);
            currentPos++;
            return token;
        }
        return new Token(TokenType.EOF, "EOF", -1, -1);
    }

    private Token consume() {
        Token token = advance();
        this.lastConsumedToken = token;
        recorder.recordToken(token);
        return token;
    }

    public CompUnitNode parse() {
        return parseCompUnit();
    }

    private CompUnitNode parseCompUnit() {
        List<DeclNode> decls = new ArrayList<>();
        List<FuncDefNode> funcDefs = new ArrayList<>();
        MainFuncDefNode mainFuncDef;

        int startLine = peek().getLineNumber();

        // 循环解析 {Decl}
        while (peek().getType() == TokenType.CONSTTK ||
                peek().getType() == TokenType.STATICTK ||
                (peek().getType() == TokenType.INTTK && peekNext().getType() != TokenType.LPARENT && peekNext().getType() != TokenType.MAINTK)) {
            decls.add(parseDecl());
        }

        // 循环解析 {FuncDef}
        while (peek().getType() == TokenType.VOIDTK ||
                (peek().getType() == TokenType.INTTK && peekNext().getType() != TokenType.MAINTK)) {
            funcDefs.add(parseFuncDef());
        }

        // 解析 MainFuncDef
        mainFuncDef = parseMainFuncDef();

        recorder.recordSyntax("CompUnit");
        return new CompUnitNode(decls, funcDefs, mainFuncDef, startLine);
    }

    private DeclNode parseDecl() {
        if (peek().getType() == TokenType.CONSTTK) {
            return parseConstDecl();
        } else {
            return parseVarDecl();
        }
    }

    private ConstDeclNode parseConstDecl() {
        int startLine = peek().getLineNumber();
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
        return new ConstDeclNode(constToken, bType, constDefs, semicn, startLine);
    }

    private BTypeNode parseBType() {
        Token typeToken = matchAndConsume(TokenType.INTTK, 'z');
        recorder.recordSyntax("BType");
        return new BTypeNode(typeToken);
    }

    private ConstDefNode parseConstDef() {
        Token ident = consume();
        List<Token> lBracks = new ArrayList<>();
        List<ConstExpNode> constExps = new ArrayList<>();
        List<Token> rBracks = new ArrayList<>();

        while (peek().getType() == TokenType.LBRACK) {
            lBracks.add(consume());
            constExps.add(parseConstExp());
            rBracks.add(matchAndConsume(TokenType.RBRACK, 'k'));
        }

        Token assignToken = matchAndConsume(TokenType.ASSIGN, 'z'); // Should not happen here based on grammar
        ConstInitValNode constInitVal = parseConstInitVal();
        recorder.recordSyntax("ConstDef");
        return new ConstDefNode(ident, lBracks, constExps, rBracks, assignToken, constInitVal);
    }

    private ConstExpNode parseConstExp() {
        AddExpNode addExp = parseAddExp();
        recorder.recordSyntax("ConstExp");
        return new ConstExpNode(addExp);
    }

    private AddExpNode parseAddExp() {
        // TODO: Phase 3 will implement full expression parsing.
        System.out.println("Parsing AddExp... (Simplified version)");
        Token number = matchAndConsume(TokenType.INTCON, 'z');
        recorder.recordSyntax("AddExp");
        return new AddExpNode(number);
    }

    private ConstInitValNode parseConstInitVal() {
        if (peek().getType() == TokenType.LBRACE) {
            Token lBrace = consume();
            List<ConstExpNode> arrayInit = new ArrayList<>();
            List<Token> commas = new ArrayList<>();

            if (peek().getType() != TokenType.RBRACE) {
                arrayInit.add(parseConstExp());
                while (peek().getType() == TokenType.COMMA) {
                    commas.add(consume());
                    arrayInit.add(parseConstExp());
                }
            }
            Token rBrace = matchAndConsume(TokenType.RBRACE, 'z');
            recorder.recordSyntax("ConstInitVal");
            return new ConstInitValNode(lBrace, arrayInit, commas, rBrace);
        } else {
            ConstExpNode singleInit = parseConstExp();
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
        Token ident = consume();
        List<Token> lBracks = new ArrayList<>();
        List<ConstExpNode> constExps = new ArrayList<>();
        List<Token> rBracks = new ArrayList<>();

        while (peek().getType() == TokenType.LBRACK) {
            lBracks.add(consume());
            constExps.add(parseConstExp());
            rBracks.add(matchAndConsume(TokenType.RBRACK, 'k'));
        }

        Token assignToken = null;
        InitValNode initVal = null;
        if (peek().getType() == TokenType.ASSIGN) {
            assignToken = consume();
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
                arrayInit.add(parseExp());
                while (peek().getType() == TokenType.COMMA) {
                    commas.add(consume());
                    arrayInit.add(parseExp());
                }
            }
            Token rBrace = matchAndConsume(TokenType.RBRACE, 'z');
            recorder.recordSyntax("InitVal");
            return new InitValNode(lBrace, arrayInit, commas, rBrace);
        } else {
            ExpNode singleInit = parseExp();
            recorder.recordSyntax("InitVal");
            return new InitValNode(singleInit);
        }
    }

    private ExpNode parseExp() {
        // 根据文法 Exp -> AddExp
        AddExpNode addExp = parseAddExp();
        recorder.recordSyntax("Exp");
        return new ExpNode(addExp);
    }

    private FuncDefNode parseFuncDef() {
        FuncTypeNode funcType = parseFuncType();
        Token ident = consume();
        Token lparen = consume();

        FuncFParamsNode funcFParams = null;
        if (peek().getType() != TokenType.RPARENT) {
            funcFParams = parseFuncFParams();
        }

        Token rparen = matchAndConsume(TokenType.RPARENT, 'j');
        BlockNode block = parseBlock();
        recorder.recordSyntax("FuncDef");
        return new FuncDefNode(funcType, ident, lparen, funcFParams, rparen, block);
    }

    private FuncTypeNode parseFuncType() {
        Token typeToken = consume();
        recorder.recordSyntax("FuncType");
        return new FuncTypeNode(typeToken);
    }

    private FuncFParamsNode parseFuncFParams() {
        // TODO: Phase 2 will implement this fully.
        System.out.println("Parsing FuncFParams... (Simplified version)");
        while (peek().getType() != TokenType.RPARENT && peek().getType() != TokenType.EOF) {
            consume();
        }
        recorder.recordSyntax("FuncFParams");
        return new FuncFParamsNode(-1);
    }

    private BlockNode parseBlock() {
        // Phase 1: 仍然是简化版
        Token lBrace = consume();
        while (peek().getType() != TokenType.RBRACE && peek().getType() != TokenType.EOF) {
            consume();
        }
        Token rBrace = matchAndConsume(TokenType.RBRACE, 'z'); // A missing } is a complex error, placeholder for now
        recorder.recordSyntax("Block");
        return new BlockNode(lBrace, rBrace);
    }

    private MainFuncDefNode parseMainFuncDef() {
        Token intToken = consume();
        Token mainToken = consume();
        Token lparen = consume();
        Token rparen = matchAndConsume(TokenType.RPARENT, 'j');
        BlockNode block = parseBlock();
        recorder.recordSyntax("MainFuncDef");
        return new MainFuncDefNode(intToken, mainToken, lparen, rparen, block);
    }

    private Token matchAndConsume(TokenType expectedType, char errorCode) {
        if (peek().getType() == expectedType) {
            return consume(); // 正确情况
        } else {
            // 错误！
            int line = (lastConsumedToken != null) ? lastConsumedToken.getLineNumber() : peek().getLineNumber();
            ErrorReporter.addError(line, errorCode);
            return null; // 错误恢复：假装匹配，返回null
        }
    }

}
