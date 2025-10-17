package io.github.tomorrow615.compiler.frontend.parser;

import io.github.tomorrow615.compiler.frontend.ast.BlockItemNode;
import io.github.tomorrow615.compiler.frontend.ast.decl.DeclNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.CondNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.ExpNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.LValNode;
import io.github.tomorrow615.compiler.frontend.ast.stmt.*;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import io.github.tomorrow615.compiler.frontend.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

public class StatementParser {
    private final Parser mainParser;
    private final ExpressionParser expressionParser;

    public StatementParser(Parser mainParser, ExpressionParser expressionParser) {
        this.mainParser = mainParser;
        this.expressionParser = expressionParser;
    }

    // 语句块 Block → '{' { BlockItem } '}'
    public BlockNode parseBlock() {
        Token lBrace = mainParser.consume();
        List<BlockItemNode> blockItems = new ArrayList<>();
        while (mainParser.peek().getType() != TokenType.RBRACE) {
            blockItems.add(this.parseBlockItem());
        }
        Token rBrace = mainParser.consume();
        mainParser.getRecorder().recordSyntax("Block");
        return new BlockNode(lBrace, blockItems, rBrace);
    }

    // 语句块项 BlockItem → Decl | Stmt
    public BlockItemNode parseBlockItem() {
        if (mainParser.peek().getType() == TokenType.CONSTTK ||
                mainParser.peek().getType() == TokenType.INTTK ||
                mainParser.peek().getType() == TokenType.STATICTK) {
            return mainParser.parseDecl();
        } else {
            return this.parseStmt();
        }
    }

    // 语句 Stmt → LVal '=' Exp ';' // i
    // | [Exp] ';' // i
    // | Block
    // | 'if' '(' Cond ')' Stmt [ 'else' Stmt ] // j
    // | 'for' '(' [ForStmt] ';' [Cond] ';' [ForStmt] ')' Stmt
    // | 'break' ';' // i
    // | 'continue' ';' // i
    // | 'return' [Exp] ';' // i
    // | 'printf''('StringConst {','Exp}')'';' // i j
    public StmtNode parseStmt() {
        TokenType currentType = mainParser.peek().getType();
        StmtNode parsedStmtNode;

        if (currentType == TokenType.LBRACE) {
            parsedStmtNode = this.parseBlock();
        } else if (currentType == TokenType.IFTK) {
            parsedStmtNode = this.parseIfStmt();
        } else if (currentType == TokenType.FORTK) {
            parsedStmtNode = this.parseForStmt();
        } else if (currentType == TokenType.BREAKTK) {
            parsedStmtNode = this.parseBreakStmt();
        } else if (currentType == TokenType.CONTINUETK) {
            parsedStmtNode = this.parseContinueStmt();
        } else if (currentType == TokenType.RETURNTK) {
            parsedStmtNode = this.parseReturnStmt();
        } else if (currentType == TokenType.PRINTFTK) {
            parsedStmtNode = this.parsePrintfStmt();
        } else {
            parsedStmtNode = this.parseAssignOrExpStmt();
        }

        mainParser.getRecorder().recordSyntax("Stmt");
        return parsedStmtNode;
    }

    // | 'if' '(' Cond ')' Stmt [ 'else' Stmt ] // j
    private StmtNode parseIfStmt() {
        Token ifToken = mainParser.consume();
        Token lparen = mainParser.consume();
        CondNode cond = expressionParser.parseCond();
        Token rparen = mainParser.matchAndConsume(TokenType.RPARENT, 'j');
        StmtNode thenStmt = this.parseStmt();

        if (mainParser.peek().getType() == TokenType.ELSETK) {
            Token elseToken = mainParser.consume();
            StmtNode elseStmt = this.parseStmt();
            return new IfStmtNode(ifToken, lparen, cond, rparen, thenStmt, elseToken, elseStmt);
        } else {
            return new IfStmtNode(ifToken, lparen, cond, rparen, thenStmt);
        }
    }

    // | 'for' '(' [ForStmt] ';' [Cond] ';' [ForStmt] ')' Stmt
    private StmtNode parseForStmt() {
        Token forToken = mainParser.consume();
        Token lparen = mainParser.consume();

        ForSubStmtNode initStmt = null;
        if (mainParser.peek().getType() != TokenType.SEMICN) {
            initStmt = this.parseForSubStmt();
        }

        Token firstSemicn = mainParser.consume();

        CondNode cond = null;
        if (mainParser.peek().getType() != TokenType.SEMICN) {
            cond = expressionParser.parseCond();
        }

        Token secondSemicn = mainParser.consume();

        ForSubStmtNode updateStmt = null;
        if (mainParser.peek().getType() != TokenType.RPARENT) {
            updateStmt = this.parseForSubStmt();
        }

        Token rparen = mainParser.consume();
        StmtNode bodyStmt = this.parseStmt();

        return new ForStmtNode(forToken, lparen, initStmt, firstSemicn, cond, secondSemicn, updateStmt, rparen, bodyStmt);
    }

    // 语句 ForStmt → LVal '=' Exp { ',' LVal '=' Exp }
    private ForSubStmtNode parseForSubStmt() {
        List<LValNode> lVals = new ArrayList<>();
        List<Token> assignTokens = new ArrayList<>();
        List<ExpNode> exps = new ArrayList<>();
        List<Token> commas = new ArrayList<>();

        lVals.add(mainParser.parseLVal(false));
        assignTokens.add(mainParser.consume());
        exps.add(expressionParser.parseExp());

        while (mainParser.peek().getType() == TokenType.COMMA) {
            commas.add(mainParser.consume());
            lVals.add(mainParser.parseLVal(false));
            assignTokens.add(mainParser.consume());
            exps.add(expressionParser.parseExp());
        }

        mainParser.getRecorder().recordSyntax("ForStmt");
        return new ForSubStmtNode(lVals, assignTokens, exps, commas);
    }

    // | 'break' ';' // i
    private StmtNode parseBreakStmt() {
        Token breakToken = mainParser.consume();
        Token semicn = mainParser.matchAndConsume(TokenType.SEMICN, 'i');
        return new BreakStmtNode(breakToken, semicn);
    }

    // | 'continue' ';' // i
    private StmtNode parseContinueStmt() {
        Token continueToken = mainParser.consume();
        Token semicn = mainParser.matchAndConsume(TokenType.SEMICN, 'i');
        return new ContinueStmtNode(continueToken, semicn);
    }

    // | 'return' [Exp] ';' // i
    private StmtNode parseReturnStmt() {
        Token returnToken = mainParser.consume();
        ExpNode exp = null;
        if (mainParser.peek().getType() != TokenType.SEMICN) {
            exp = expressionParser.parseExp();
        }
        Token semicn = mainParser.matchAndConsume(TokenType.SEMICN, 'i');
        return new ReturnStmtNode(returnToken, exp, semicn);
    }

    // | 'printf''('StringConst {','Exp}')'';' // i j
    private StmtNode parsePrintfStmt() {
        Token printfToken = mainParser.consume();
        Token lparen = mainParser.consume();
        Token formatString = mainParser.consume();

        List<Token> commas = new ArrayList<>();
        List<ExpNode> exps = new ArrayList<>();

        while (mainParser.peek().getType() == TokenType.COMMA) {
            commas.add(mainParser.consume());
            exps.add(expressionParser.parseExp());
        }

        Token rparen = mainParser.matchAndConsume(TokenType.RPARENT, 'j');
        Token semicn = mainParser.matchAndConsume(TokenType.SEMICN, 'i');

        return new PrintfStmtNode(printfToken, lparen, formatString, commas, exps, rparen, semicn);
    }

    private boolean isAssignment() {
        int initialPos = mainParser.savePosition();
        try {
            int tempPos = initialPos;
            if (tempPos >= mainParser.getTokens().size() || mainParser.getTokens().get(tempPos).getType() != TokenType.IDENFR) {
                return false;
            }
            tempPos++;

            while (tempPos < mainParser.getTokens().size() && mainParser.getTokens().get(tempPos).getType() == TokenType.LBRACK) {
                tempPos++;
                int bracketLevel = 1;
                while (bracketLevel > 0 && tempPos < mainParser.getTokens().size()) {
                    TokenType type = mainParser.getTokens().get(tempPos).getType();
                    if (type == TokenType.LBRACK) bracketLevel++;
                    else if (type == TokenType.RBRACK) bracketLevel--;
                    else if (type == TokenType.EOF) return false;
                    tempPos++;
                }
            }

            return tempPos < mainParser.getTokens().size() && mainParser.getTokens().get(tempPos).getType() == TokenType.ASSIGN;
        } finally {
            mainParser.restorePosition(initialPos);
        }
    }

    // 语句 Stmt → LVal '=' Exp ';' // i
    // | [Exp] ';' // i
    private StmtNode parseAssignOrExpStmt() {
        if (this.isAssignment()) {
            LValNode lVal = mainParser.parseLVal(false);
            Token assignToken = mainParser.matchAndConsume(TokenType.ASSIGN, 'z');
            ExpNode exp = expressionParser.parseExp();
            Token semicn = mainParser.matchAndConsume(TokenType.SEMICN, 'i');
            return new AssignStmtNode(lVal, assignToken, exp, semicn);
        } else {
            ExpNode exp = null;
            if (mainParser.peek().getType() != TokenType.SEMICN) {
                exp = expressionParser.parseExp();
            }
            Token semicn = mainParser.matchAndConsume(TokenType.SEMICN, 'i');
            return new ExpStmtNode(exp, semicn);
        }
    }
}