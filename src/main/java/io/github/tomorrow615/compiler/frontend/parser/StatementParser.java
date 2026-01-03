package io.github.tomorrow615.compiler.frontend.parser;

import io.github.tomorrow615.compiler.frontend.ast.BlockItemNode;
import io.github.tomorrow615.compiler.frontend.ast.decl.*;
import io.github.tomorrow615.compiler.frontend.ast.expr.*;
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
        return new BlockNode(blockItems, lBrace.getLineNumber(), rBrace.getLineNumber());
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
    // | 'if' '(' 'int' Ident '=' InitVal ')' Stmt [ 'else' Stmt ]
    private StmtNode parseIfStmt() {
        Token ifToken = mainParser.consume();
        mainParser.consume();

        /* [NEW] Support for: if (int a = 1) ...
           Desc: Transforms 'if (int a = 1) stmt' into '{ int a = 1; if (a) stmt }'
        if (mainParser.peek().getType() == TokenType.INTTK) {
            Token intToken = mainParser.consume(); // int
            BTypeNode bType = new BTypeNode(intToken);
            Token ident = mainParser.consume(); // Ident
            mainParser.consume(); // =
            ExpNode initExp = expressionParser.parseExp(); // InitVal (simplified to Exp)
            InitValNode initVal = new InitValNode(initExp);

            mainParser.matchAndConsume(TokenType.RPARENT, 'j'); // )

            // 1. Synthesize VarDecl: int a = 1;
            List<ConstExpNode> emptyConstExps = new ArrayList<>();
            VarDefNode varDef = new VarDefNode(ident, emptyConstExps, initVal);
            List<VarDefNode> varDefs = new ArrayList<>();
            varDefs.add(varDef);
            VarDeclNode varDecl = new VarDeclNode(false, bType, varDefs, intToken.getLineNumber());

            // 2. Synthesize Condition: if (a)
            // LVal -> PrimaryExp -> UnaryExp -> MulExp -> AddExp -> RelExp -> EqExp -> LAndExp -> LOrExp -> Cond
            LValNode lVal = new LValNode(ident);

            PrimaryExpNode primaryExp = new PrimaryExpNode(lVal);

            UnaryExpNode unaryExp = new UnaryExpNode(primaryExp);

            List<UnaryExpNode> unaryExps = new ArrayList<>(); unaryExps.add(unaryExp);
            MulExpNode mulExp = new MulExpNode(unaryExps, new ArrayList<>());

            List<MulExpNode> mulExps = new ArrayList<>(); mulExps.add(mulExp);
            AddExpNode addExp = new AddExpNode(mulExps, new ArrayList<>());

            List<AddExpNode> addExps = new ArrayList<>(); addExps.add(addExp);
            RelExpNode relExp = new RelExpNode(addExps, new ArrayList<>());

            List<RelExpNode> relExps = new ArrayList<>(); relExps.add(relExp);
            EqExpNode eqExp = new EqExpNode(relExps, new ArrayList<>());

            List<EqExpNode> eqExps = new ArrayList<>(); eqExps.add(eqExp);
            LAndExpNode lAndExp = new LAndExpNode(eqExps, new ArrayList<>());

            List<LAndExpNode> lAndExps = new ArrayList<>(); lAndExps.add(lAndExp);
            LOrExpNode lOrExp = new LOrExpNode(lAndExps, new ArrayList<>());
            
            CondNode cond = new CondNode(lOrExp);

            StmtNode thenStmt = this.parseStmt();
            StmtNode elseStmt = null;
            if (mainParser.peek().getType() == TokenType.ELSETK) {
                mainParser.consume();
                elseStmt = this.parseStmt();
            }

            // 3. Create IfStmt
            IfStmtNode ifStmt;
            if (elseStmt != null) {
                ifStmt = new IfStmtNode(cond, thenStmt, elseStmt, intToken.getLineNumber());
            } else {
                ifStmt = new IfStmtNode(cond, thenStmt, intToken.getLineNumber());
            }

            // 4. Wrap directly in Block: { int a=1; if(a)... }
            List<BlockItemNode> blockItems = new ArrayList<>();
            blockItems.add(varDecl);
            blockItems.add(ifStmt);
            return new BlockNode(blockItems, ifToken.getLineNumber(), (elseStmt!=null ? elseStmt : thenStmt).getLineNumber()); // End line approximate
        }
        */

        CondNode cond = expressionParser.parseCond();
        mainParser.matchAndConsume(TokenType.RPARENT, 'j');
        StmtNode thenStmt = this.parseStmt();

        if (mainParser.peek().getType() == TokenType.ELSETK) {
            mainParser.consume();
            StmtNode elseStmt = this.parseStmt();
            return new IfStmtNode(cond, thenStmt, elseStmt, ifToken.getLineNumber());
        } else {
            return new IfStmtNode(cond, thenStmt, ifToken.getLineNumber());
        }
    }

    // | 'for' '(' [ForStmt] ';' [Cond] ';' [ForStmt] ')' Stmt
    // [NEW4] ForStmt -> BType Ident '=' InitVal (for循环内声明变量)
    private StmtNode parseForStmt() {
        Token forToken = mainParser.consume();
        mainParser.consume();

        ForSubStmtNode initStmt = null;
        /* [NEW4] 检测 for(int i = 1;;) 形式的变量声明
        VarDeclNode initDecl = null;
        if (mainParser.peek().getType() == TokenType.INTTK) {
            // 解析 int i = InitVal
            mainParser.consume(); // int
            Token ident = mainParser.matchAndConsume(TokenType.IDENFR, 'i');
            mainParser.matchAndConsume(TokenType.ASSIGN, 'k');
            InitValNode initVal = mainParser.parseInitVal();
            
            VarDefNode varDef = new VarDefNode(ident, new ArrayList<>(), initVal, false, ident.getLineNumber());
            initDecl = new VarDeclNode(new ArrayList<>(){{ add(varDef); }}, false, ident.getLineNumber());
        } else */
        if (mainParser.peek().getType() != TokenType.SEMICN) {
            initStmt = this.parseForSubStmt();
        }

        mainParser.consume();

        CondNode cond = null;
        if (mainParser.peek().getType() != TokenType.SEMICN) {
            cond = expressionParser.parseCond();
        }

        mainParser.consume();

        ForSubStmtNode updateStmt = null;
        if (mainParser.peek().getType() != TokenType.RPARENT) {
            updateStmt = this.parseForSubStmt();
        }

        mainParser.consume();
        StmtNode bodyStmt = this.parseStmt();

        // [NEW4] return new ForStmtNode(initStmt, initDecl, cond, updateStmt, bodyStmt, forToken.getLineNumber());
        return new ForStmtNode(initStmt, cond, updateStmt, bodyStmt, forToken.getLineNumber());
    }

    // 语句 ForStmt → LVal '=' Exp { ',' LVal '=' Exp }
    private ForSubStmtNode parseForSubStmt() {
        List<LValNode> lVals = new ArrayList<>();
        List<ExpNode> exps = new ArrayList<>();

        lVals.add(mainParser.parseLVal());
        mainParser.consume();
        exps.add(expressionParser.parseExp());

        while (mainParser.peek().getType() == TokenType.COMMA) {
            mainParser.consume();
            lVals.add(mainParser.parseLVal());
            mainParser.consume();
            exps.add(expressionParser.parseExp());
        }

        mainParser.getRecorder().recordSyntax("ForStmt");
        return new ForSubStmtNode(lVals, exps);
    }

    // | 'break' ';' // i
    private StmtNode parseBreakStmt() {
        Token breakToken = mainParser.consume();
        mainParser.matchAndConsume(TokenType.SEMICN, 'i');
        return new BreakStmtNode(breakToken.getLineNumber());
    }

    // | 'continue' ';' // i
    private StmtNode parseContinueStmt() {
        Token continueToken = mainParser.consume();
        mainParser.matchAndConsume(TokenType.SEMICN, 'i');
        return new ContinueStmtNode(continueToken.getLineNumber());
    }

    // | 'return' [Exp] ';' // i
    private StmtNode parseReturnStmt() {
        Token returnToken = mainParser.consume();
        ExpNode exp = null;
        if (mainParser.peek().getType() != TokenType.SEMICN) {
            exp = expressionParser.parseExp();
        }
        mainParser.matchAndConsume(TokenType.SEMICN, 'i');
        return new ReturnStmtNode(exp, returnToken.getLineNumber());
    }

    // | 'printf''('StringConst {','Exp}')'';' // i j
    private StmtNode parsePrintfStmt() {
        Token printfToken = mainParser.consume();
        mainParser.consume();
        Token formatString = mainParser.consume();

        List<ExpNode> exps = new ArrayList<>();

        while (mainParser.peek().getType() == TokenType.COMMA) {
            mainParser.consume();
            exps.add(expressionParser.parseExp());
        }

        mainParser.matchAndConsume(TokenType.RPARENT, 'j');
        mainParser.matchAndConsume(TokenType.SEMICN, 'i');
        return new PrintfStmtNode(formatString, exps, printfToken.getLineNumber());
    }

    private boolean isAssignment() {
        int initialPos = mainParser.savePosition();
        try {
            int tempPos = initialPos;
            if (tempPos >= mainParser.getTokens().size() ||
                    mainParser.getTokens().get(tempPos).getType() != TokenType.IDENFR) {
                return false;
            }
            tempPos++;

            while (tempPos < mainParser.getTokens().size() &&
                    mainParser.getTokens().get(tempPos).getType() == TokenType.LBRACK) {
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

            return tempPos < mainParser.getTokens().size() &&
                    mainParser.getTokens().get(tempPos).getType() == TokenType.ASSIGN;
        } finally {
            mainParser.restorePosition(initialPos);
        }
    }

    // 语句 Stmt → LVal '=' Exp ';' // i
    // | [Exp] ';' // i
    private StmtNode parseAssignOrExpStmt() {
        if (this.isAssignment()) {
            LValNode lVal = mainParser.parseLVal();
            mainParser.consume();
            ExpNode exp = expressionParser.parseExp();
            mainParser.matchAndConsume(TokenType.SEMICN, 'i');
            return new AssignStmtNode(lVal, exp);
        } else {
            ExpNode exp = null;
            Token firstToken = mainParser.peek();
            if (mainParser.peek().getType() != TokenType.SEMICN) {
                exp = expressionParser.parseExp();
            }
            mainParser.matchAndConsume(TokenType.SEMICN, 'i');
            return new ExpStmtNode(exp, firstToken.getLineNumber());
        }
    }
}