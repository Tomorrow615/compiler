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

    public BlockItemNode parseBlockItem() {
        if (mainParser.peek().getType() == TokenType.CONSTTK ||
                mainParser.peek().getType() == TokenType.INTTK ||
                mainParser.peek().getType() == TokenType.STATICTK) {
            // 声明的解析逻辑仍在主Parser中
            return mainParser.parseDecl();
        } else {
            return this.parseStmt();
        }
    }

    public StmtNode parseStmt() {
        TokenType currentType = mainParser.peek().getType();

        if (currentType == TokenType.LBRACE) {
            BlockNode blockNode = this.parseBlock();
            mainParser.getRecorder().recordSyntax("Stmt");
            return blockNode;
        } else if (currentType == TokenType.IFTK) {
            return this.parseIfStmt();
        } else if (currentType == TokenType.FORTK) {
            return this.parseForStmt();
        } else if (currentType == TokenType.BREAKTK) {
            return this.parseBreakStmt();
        } else if (currentType == TokenType.CONTINUETK) {
            return this.parseContinueStmt();
        } else if (currentType == TokenType.RETURNTK) {
            return this.parseReturnStmt();
        } else if (currentType == TokenType.PRINTFTK) {
            return this.parsePrintfStmt();
        } else {
            return this.parseAssignOrExpStmt();
        }
    }

    public BlockNode parseBlock() {
        Token lBrace = mainParser.matchAndConsume(TokenType.LBRACE, 'z');
        List<BlockItemNode> blockItems = new ArrayList<>();

        while (mainParser.peek().getType() != TokenType.RBRACE && mainParser.peek().getType() != TokenType.EOF) {
            blockItems.add(this.parseBlockItem());
        }

        Token rBrace = mainParser.matchAndConsume(TokenType.RBRACE, 'z');
        mainParser.getRecorder().recordSyntax("Block");
        return new BlockNode(lBrace, blockItems, rBrace);
    }

    private StmtNode parseBreakStmt() {
        Token breakToken = mainParser.matchAndConsume(TokenType.BREAKTK, 'z');
        Token semicn = mainParser.matchAndConsume(TokenType.SEMICN, 'i');
        mainParser.getRecorder().recordSyntax("Stmt");
        return new BreakStmtNode(breakToken, semicn);
    }

    private StmtNode parseContinueStmt() {
        Token continueToken = mainParser.matchAndConsume(TokenType.CONTINUETK, 'z');
        Token semicn = mainParser.matchAndConsume(TokenType.SEMICN, 'i');
        mainParser.getRecorder().recordSyntax("Stmt");
        return new ContinueStmtNode(continueToken, semicn);
    }

    private StmtNode parseReturnStmt() {
        Token returnToken = mainParser.matchAndConsume(TokenType.RETURNTK, 'z');
        ExpNode exp = null;
        if (mainParser.peek().getType() != TokenType.SEMICN) {
            exp = expressionParser.parseExp();
        }
        Token semicn = mainParser.matchAndConsume(TokenType.SEMICN, 'i');
        mainParser.getRecorder().recordSyntax("Stmt");
        return new ReturnStmtNode(returnToken, exp, semicn);
    }

    private StmtNode parseIfStmt() {
        Token ifToken = mainParser.matchAndConsume(TokenType.IFTK, 'z');
        Token lparen = mainParser.matchAndConsume(TokenType.LPARENT, 'j');
        CondNode cond = expressionParser.parseCond();
        Token rparen = mainParser.matchAndConsume(TokenType.RPARENT, 'j');
        StmtNode thenStmt = this.parseStmt();

        Token elseToken = null;
        StmtNode elseStmt = null;

        if (mainParser.peek().getType() == TokenType.ELSETK) {
            elseToken = mainParser.matchAndConsume(TokenType.ELSETK, 'z');
            elseStmt = this.parseStmt();
        }

        mainParser.getRecorder().recordSyntax("Stmt");
        return new IfStmtNode(ifToken, lparen, cond, rparen, thenStmt, elseToken, elseStmt);
    }

    private ForSubStmtNode parseForSubStmt() {
        List<LValNode> lVals = new ArrayList<>();
        List<Token> assignTokens = new ArrayList<>();
        List<ExpNode> exps = new ArrayList<>();
        List<Token> commas = new ArrayList<>();

        lVals.add(mainParser.parseLVal(false));
        assignTokens.add(mainParser.matchAndConsume(TokenType.ASSIGN, 'z'));
        exps.add(expressionParser.parseExp());

        while (mainParser.peek().getType() == TokenType.COMMA) {
            commas.add(mainParser.matchAndConsume(TokenType.COMMA, 'z'));
            lVals.add(mainParser.parseLVal(false));
            assignTokens.add(mainParser.matchAndConsume(TokenType.ASSIGN, 'z'));
            exps.add(expressionParser.parseExp());
        }

        mainParser.getRecorder().recordSyntax("ForStmt");
        return new ForSubStmtNode(lVals, assignTokens, exps, commas);
    }

    private StmtNode parseForStmt() {
        Token forToken = mainParser.matchAndConsume(TokenType.FORTK, 'z');
        Token lparen = mainParser.matchAndConsume(TokenType.LPARENT, 'j');

        ForSubStmtNode initStmt = null;
        if (mainParser.peek().getType() != TokenType.SEMICN) {
            initStmt = this.parseForSubStmt();
        }

        Token firstSemicn = mainParser.matchAndConsume(TokenType.SEMICN, 'i');

        CondNode cond = null;
        if (mainParser.peek().getType() != TokenType.SEMICN) {
            cond = expressionParser.parseCond();
        }

        Token secondSemicn = mainParser.matchAndConsume(TokenType.SEMICN, 'i');

        ForSubStmtNode updateStmt = null;
        if (mainParser.peek().getType() != TokenType.RPARENT) {
            updateStmt = this.parseForSubStmt();
        }

        Token rparen = mainParser.matchAndConsume(TokenType.RPARENT, 'j');
        StmtNode bodyStmt = this.parseStmt();

        mainParser.getRecorder().recordSyntax("Stmt");
        return new ForStmtNode(forToken, lparen, initStmt, firstSemicn, cond, secondSemicn, updateStmt, rparen, bodyStmt);
    }

    private StmtNode parsePrintfStmt() {
        Token printfToken = mainParser.matchAndConsume(TokenType.PRINTFTK, 'z');
        Token lparen = mainParser.matchAndConsume(TokenType.LPARENT, 'j');
        Token formatString = mainParser.matchAndConsume(TokenType.STRCON, 'z');

        List<Token> commas = new ArrayList<>();
        List<ExpNode> exps = new ArrayList<>();

        while (mainParser.peek().getType() == TokenType.COMMA) {
            commas.add(mainParser.matchAndConsume(TokenType.COMMA, 'z'));
            exps.add(expressionParser.parseExp());
        }

        Token rparen = mainParser.matchAndConsume(TokenType.RPARENT, 'j');
        Token semicn = mainParser.matchAndConsume(TokenType.SEMICN, 'i');

        mainParser.getRecorder().recordSyntax("Stmt");
        return new PrintfStmtNode(printfToken, lparen, formatString, commas, exps, rparen, semicn);
    }

    private boolean isAssignment() {
        int initialPos = mainParser.savePosition();
        try { // 使用 try-finally 确保位置指针一定会被恢复
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

    private StmtNode parseAssignOrExpStmt() {
        if (this.isAssignment()) {
            LValNode lVal = mainParser.parseLVal(false);
            Token assignToken = mainParser.matchAndConsume(TokenType.ASSIGN, 'z');
            ExpNode exp = expressionParser.parseExp();
            Token semicn = mainParser.matchAndConsume(TokenType.SEMICN, 'i');
            mainParser.getRecorder().recordSyntax("Stmt");
            return new AssignStmtNode(lVal, assignToken, exp, semicn);
        } else {
            ExpNode exp = null;
            if (mainParser.peek().getType() != TokenType.SEMICN) {
                exp = expressionParser.parseExp();
            }
            Token semicn = mainParser.matchAndConsume(TokenType.SEMICN, 'i');
            mainParser.getRecorder().recordSyntax("Stmt");
            return new ExpStmtNode(exp, semicn);
        }
    }
}