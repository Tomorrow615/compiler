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

    private Token peek(int k) {
        if (currentPos + k < tokens.size()) {
            return tokens.get(currentPos + k);
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

    private Token matchAndConsume(TokenType expectedType, char errorCode) {
        if (peek().getType() == expectedType) {
            return consume();
        } else {
            int line = (lastConsumedToken != null) ? lastConsumedToken.getLineNumber() : peek().getLineNumber();
            ErrorReporter.addError(line, errorCode);
            return null; // 错误恢复：假装匹配，返回null
        }
    }

    private int savePosition() {
        return this.currentPos;
    }

    private void restorePosition(int position) {
        this.currentPos = position;
    }

    public CompUnitNode parse() {
        return parseCompUnit();
    }

    private CompUnitNode parseCompUnit() {
        List<DeclNode> decls = new ArrayList<>();
        List<FuncDefNode> funcDefs = new ArrayList<>();
        MainFuncDefNode mainFuncDef;

        int startLine = peek().getLineNumber();

        // 修正后的 Decl 解析循环
        while (peek().getType() == TokenType.CONSTTK ||
                (peek().getType() == TokenType.INTTK && peek(2).getType() != TokenType.LPARENT)) {
            decls.add(parseDecl());
        }

        // 修正后的 FuncDef 解析循环
        while ((peek().getType() == TokenType.VOIDTK ||
                (peek().getType() == TokenType.INTTK && peekNext().getType() != TokenType.MAINTK)) &&
                peek(2).getType() == TokenType.LPARENT) {
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
        Token constToken = matchAndConsume(TokenType.CONSTTK, 'z');
        int startLine = (constToken != null) ? constToken.getLineNumber() : lastConsumedToken.getLineNumber();

        BTypeNode bType = parseBType();
        List<ConstDefNode> constDefs = new ArrayList<>();
        constDefs.add(parseConstDef());

        while (peek().getType() == TokenType.COMMA) {
            matchAndConsume(TokenType.COMMA, 'z');
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
        Token ident = matchAndConsume(TokenType.IDENFR, 'z');

        List<Token> lBracks = new ArrayList<>();
        List<ConstExpNode> constExps = new ArrayList<>();
        List<Token> rBracks = new ArrayList<>();

        while (peek().getType() == TokenType.LBRACK) {
            lBracks.add(matchAndConsume(TokenType.LBRACK, 'z'));
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
        Token ident = matchAndConsume(TokenType.IDENFR, 'z');

        List<Token> lBracks = new ArrayList<>();
        List<ConstExpNode> constExps = new ArrayList<>();
        List<Token> rBracks = new ArrayList<>();

        while (peek().getType() == TokenType.LBRACK) {
            lBracks.add(matchAndConsume(TokenType.LBRACK, 'z'));
            constExps.add(parseConstExp());
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
        List<FuncFParamNode> params = new ArrayList<>();
        List<Token> commas = new ArrayList<>();

        // FuncFParams -> FuncFParam { ',' FuncFParam }
        // 先解析第一个必须存在的 FuncFParam
        params.add(parseFuncFParam());

        // 循环解析 { ',' FuncFParam }
        while (peek().getType() == TokenType.COMMA) {
            commas.add(matchAndConsume(TokenType.COMMA, 'z'));
            params.add(parseFuncFParam());
        }

        recorder.recordSyntax("FuncFParams");
        return new FuncFParamsNode(params, commas);
    }

    private FuncFParamNode parseFuncFParam() {
        // FuncFParam -> BType Ident ['[' ']']
        BTypeNode bType = parseBType();
        Token ident = matchAndConsume(TokenType.IDENFR, 'z');
        Token lbrack = null;
        Token rbrack = null;

        if (peek().getType() == TokenType.LBRACK) {
            lbrack = matchAndConsume(TokenType.LBRACK, 'z');
            // 注意：函数参数的数组形式是 '[]'，中间没有表达式
            rbrack = matchAndConsume(TokenType.RBRACK, 'k');
        }

        recorder.recordSyntax("FuncFParam");
        return new FuncFParamNode(bType, ident, lbrack, rbrack);
    }

    private BlockNode parseBlock() {
        Token lBrace = matchAndConsume(TokenType.LBRACE, 'z');
        List<BlockItemNode> blockItems = new ArrayList<>();

        // 循环解析 { BlockItem }，直到遇到 '}' 或文件末尾
        while (peek().getType() != TokenType.RBRACE && peek().getType() != TokenType.EOF) {
            blockItems.add(parseBlockItem());
        }

        Token rBrace = matchAndConsume(TokenType.RBRACE, 'z');
        recorder.recordSyntax("Block");
        return new BlockNode(lBrace, blockItems, rBrace);
    }

    // 在 Parser.java 中
    private BlockItemNode parseBlockItem() {
        if (peek().getType() == TokenType.CONSTTK ||
                peek().getType() == TokenType.INTTK ||
                peek().getType() == TokenType.STATICTK) {
            return parseDecl();
        } else {
            // 直接解析并返回语句，不做任何额外处理
            return parseStmt();
        }
    }

    // 在 Parser.java 中
    private StmtNode parseStmt() {
        TokenType currentType = peek().getType();

        if (currentType == TokenType.LBRACE) {
            // 规则: Stmt -> Block
            // 在这里，我们解析一个 Block，然后明确地把它当作一个 Stmt 来记录。
            BlockNode blockNode = parseBlock(); // parseBlock 只负责解析和打印 <Block>
            recorder.recordSyntax("Stmt"); // 我们在这里为它补上 <Stmt> 的身份
            return blockNode;
        } else if (currentType == TokenType.IFTK) {
            return parseIfStmt();
        } else if (currentType == TokenType.FORTK) {
            return parseForStmt();
        } else if (currentType == TokenType.BREAKTK) {
            return parseBreakStmt();
        } else if (currentType == TokenType.CONTINUETK) {
            return parseContinueStmt();
        } else if (currentType == TokenType.RETURNTK) {
            return parseReturnStmt();
        } else if (currentType == TokenType.PRINTFTK) {
            return parsePrintfStmt();
        } else {
            return parseAssignOrExpStmt();
        }
    }

    private StmtNode parseBreakStmt() {
        // 'break' 关键字本身的存在由 parseStmt 分发器保证，所以无需检查
        Token breakToken = matchAndConsume(TokenType.BREAKTK, 'z'); // 'z' 作为通用占位符
        // Stmt -> 'break' ';' // i
        Token semicn = matchAndConsume(TokenType.SEMICN, 'i');
        recorder.recordSyntax("Stmt");
        return new BreakStmtNode(breakToken, semicn);
    }

    private StmtNode parseContinueStmt() {
        Token continueToken = matchAndConsume(TokenType.CONTINUETK, 'z');
        // Stmt -> 'continue' ';' // i
        Token semicn = matchAndConsume(TokenType.SEMICN, 'i');
        recorder.recordSyntax("Stmt");
        return new ContinueStmtNode(continueToken, semicn);
    }

    private StmtNode parseReturnStmt() {
        Token returnToken = matchAndConsume(TokenType.RETURNTK, 'z');
        ExpNode exp = null;
        if (peek().getType() != TokenType.SEMICN) {
            exp = parseExp();
        }
        // Stmt -> 'return' [Exp] ';' // i
        Token semicn = matchAndConsume(TokenType.SEMICN, 'i');
        recorder.recordSyntax("Stmt");
        return new ReturnStmtNode(returnToken, exp, semicn);
    }

    private StmtNode parseIfStmt() {
        Token ifToken = matchAndConsume(TokenType.IFTK, 'z');
        Token lparen = matchAndConsume(TokenType.LPARENT, 'j');
        CondNode cond = parseCond();
        Token rparen = matchAndConsume(TokenType.RPARENT, 'j');
        StmtNode thenStmt = parseStmt();

        Token elseToken = null;
        StmtNode elseStmt = null;

        // 根据文法 [ 'else' Stmt ]，else 部分是可选的
        if (peek().getType() == TokenType.ELSETK) {
            elseToken = matchAndConsume(TokenType.ELSETK, 'z');
            elseStmt = parseStmt();
        }

        recorder.recordSyntax("Stmt");
        return new IfStmtNode(ifToken, lparen, cond, rparen, thenStmt, elseToken, elseStmt);
    }

    private ForSubStmtNode parseForSubStmt() {
        List<LValNode> lVals = new ArrayList<>();
        List<Token> assignTokens = new ArrayList<>();
        List<ExpNode> exps = new ArrayList<>();
        List<Token> commas = new ArrayList<>();

        // 解析第一个 LVal = Exp
        lVals.add(parseLVal(false));
        assignTokens.add(matchAndConsume(TokenType.ASSIGN, 'z'));
        exps.add(parseExp());

        // 循环解析 { ',' LVal '=' Exp }
        while (peek().getType() == TokenType.COMMA) {
            commas.add(matchAndConsume(TokenType.COMMA, 'z'));
            lVals.add(parseLVal(false));
            assignTokens.add(matchAndConsume(TokenType.ASSIGN, 'z'));
            exps.add(parseExp());
        }

        recorder.recordSyntax("ForStmt"); // 注意：文法中这个成分叫 ForStmt
        return new ForSubStmtNode(lVals, assignTokens, exps, commas);
    }

    private StmtNode parseForStmt() {
        Token forToken = matchAndConsume(TokenType.FORTK, 'z');
        Token lparen = matchAndConsume(TokenType.LPARENT, 'j');

        ForSubStmtNode initStmt = null;
        // [ForStmt]
        if (peek().getType() != TokenType.SEMICN) {
            initStmt = parseForSubStmt();
        }

        Token firstSemicn = matchAndConsume(TokenType.SEMICN, 'i');

        CondNode cond = null;
        // [Cond]
        if (peek().getType() != TokenType.SEMICN) {
            cond = parseCond();
        }

        Token secondSemicn = matchAndConsume(TokenType.SEMICN, 'i');

        ForSubStmtNode updateStmt = null;
        // [ForStmt]
        if (peek().getType() != TokenType.RPARENT) {
            updateStmt = parseForSubStmt();
        }

        Token rparen = matchAndConsume(TokenType.RPARENT, 'j');
        StmtNode bodyStmt = parseStmt();

        recorder.recordSyntax("Stmt");
        return new ForStmtNode(forToken, lparen, initStmt, firstSemicn, cond, secondSemicn, updateStmt, rparen, bodyStmt);
    }

    private StmtNode parsePrintfStmt() {
        Token printfToken = matchAndConsume(TokenType.PRINTFTK, 'z');
        Token lparen = matchAndConsume(TokenType.LPARENT, 'j');
        Token formatString = matchAndConsume(TokenType.STRCON, 'z'); // 'z' for generic error, though a specific one 'l' is mentioned for format string issues later.

        List<Token> commas = new ArrayList<>();
        List<ExpNode> exps = new ArrayList<>();

        // 循环解析 { ',' Exp }
        while (peek().getType() == TokenType.COMMA) {
            commas.add(matchAndConsume(TokenType.COMMA, 'z'));
            exps.add(parseExp());
        }

        Token rparen = matchAndConsume(TokenType.RPARENT, 'j');
        Token semicn = matchAndConsume(TokenType.SEMICN, 'i');

        recorder.recordSyntax("Stmt");
        return new PrintfStmtNode(printfToken, lparen, formatString, commas, exps, rparen, semicn);
    }

    private LValNode parseLVal(boolean isSpeculative) { // <-- 修改后
        Token ident = matchAndConsume(TokenType.IDENFR, 'z');
        List<Token> lBracks = new ArrayList<>();
        List<ExpNode> arrayExps = new ArrayList<>();
        List<Token> rBracks = new ArrayList<>();

        while (peek().getType() == TokenType.LBRACK) {
            lBracks.add(matchAndConsume(TokenType.LBRACK, 'z'));
            arrayExps.add(parseExp());
            rBracks.add(matchAndConsume(TokenType.RBRACK, 'k'));
        }

        // 只有在不是试探性解析（即确认是LVal）时才记录输出
        if (!isSpeculative) {
            recorder.recordSyntax("LVal");
        }
        return new LValNode(ident, lBracks, arrayExps, rBracks);
    }

    // 这是一个"轻量级"的先行查看函数，它只看不消费，没有副作用
    private boolean isAssignment() {
        // 保存当前真实位置
        int initialPos = savePosition();
        int tempPos = initialPos;

        // 如果连第一个 token 都不存在，或者不是 IDENFR，肯定不是赋值语句
        if (tempPos >= tokens.size() || tokens.get(tempPos).getType() != TokenType.IDENFR) {
            return false;
        }
        tempPos++; // 虚拟指针跳过 IDENFR

        // 跳过所有可能的数组下标 `[...]`
        while (tempPos < tokens.size() && tokens.get(tempPos).getType() == TokenType.LBRACK) {
            tempPos++; // 跳过 '['

            // 使用括号层级计数来找到匹配的 ']'
            int bracketLevel = 1;
            while (bracketLevel > 0 && tempPos < tokens.size()) {
                TokenType type = tokens.get(tempPos).getType();
                if (type == TokenType.LBRACK) {
                    bracketLevel++;
                } else if (type == TokenType.RBRACK) {
                    bracketLevel--;
                } else if (type == TokenType.EOF) {
                    // 如果在找到匹配的 ']' 之前就结束了，说明语法有误，肯定不是赋值语句
                    return false;
                }
                tempPos++;
            }
        }

        // 最终检查 LVal 后面的 token 是不是 '='
        if (tempPos < tokens.size() && tokens.get(tempPos).getType() == TokenType.ASSIGN) {
            return true;
        }

        return false;
    }

    // 第二步：重写 parseAssignOrExpStmt 方法
    private StmtNode parseAssignOrExpStmt() {
        // 使用新的、无副作用的先行查看来做决定
        if (isAssignment()) {
            // 确定是赋值语句，现在进行正式解析
            LValNode lVal = parseLVal(false); // false表示这不是试探
            Token assignToken = matchAndConsume(TokenType.ASSIGN, 'z');
            ExpNode exp = parseExp();
            Token semicn = matchAndConsume(TokenType.SEMICN, 'i');
            recorder.recordSyntax("Stmt");
            return new AssignStmtNode(lVal, assignToken, exp, semicn);
        } else {
            // 确定是表达式语句，直接解析
            ExpNode exp = null;
            if (peek().getType() != TokenType.SEMICN) {
                exp = parseExp();
            }
            Token semicn = matchAndConsume(TokenType.SEMICN, 'i');
            recorder.recordSyntax("Stmt");
            return new ExpStmtNode(exp, semicn);
        }
    }

    private CondNode parseCond() {
        // ExpNode exp = parseExp(); // <-- 修改前
        LOrExpNode lorExp = parseLOrExp(); // <-- 修改后
        recorder.recordSyntax("Cond");
        // return new CondNode(exp); // <-- 修改前
        return new CondNode(lorExp); // <-- 修改后
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

    private PrimaryExpNode parsePrimaryExp() {
        PrimaryExpNode node;
        if (peek().getType() == TokenType.LPARENT) {
            matchAndConsume(TokenType.LPARENT, 'j');
            ExpNode exp = parseExp();
            matchAndConsume(TokenType.RPARENT, 'j');
            node = new PrimaryExpNode(exp);
        } else if (peek().getType() == TokenType.IDENFR) {
            LValNode lVal = parseLVal(false); // 确定是 LVal，非试探
            node = new PrimaryExpNode(lVal);
        } else { // Number
            Token numberToken = matchAndConsume(TokenType.INTCON, 'z');
            NumberNode numberNode = new NumberNode(numberToken);
            recorder.recordSyntax("Number");
            node = new PrimaryExpNode(numberNode);
        }
        recorder.recordSyntax("PrimaryExp");
        return node;
    }

    private UnaryExpNode parseUnaryExp() {
        UnaryExpNode node;
        if (peek().getType() == TokenType.PLUS || peek().getType() == TokenType.MINU || peek().getType() == TokenType.NOT) {
            Token opToken = consume();
            UnaryOpNode opNode = new UnaryOpNode(opToken);
            recorder.recordSyntax("UnaryOp");
            UnaryExpNode exp = parseUnaryExp();
            node = new UnaryExpNode(opNode, exp);
        } else if (peek().getType() == TokenType.IDENFR && peekNext().getType() == TokenType.LPARENT) {
            Token ident = matchAndConsume(TokenType.IDENFR, 'z');
            matchAndConsume(TokenType.LPARENT, 'j');
            FuncRParamsNode params = null;
            if (peek().getType() != TokenType.RPARENT) {
                params = parseFuncRParams();
            }
            matchAndConsume(TokenType.RPARENT, 'j');
            node = new UnaryExpNode(ident, params);
        } else {
            PrimaryExpNode primaryExp = parsePrimaryExp();
            node = new UnaryExpNode(primaryExp);
        }
        recorder.recordSyntax("UnaryExp");
        return node;
    }

    private FuncRParamsNode parseFuncRParams() {
        List<ExpNode> params = new ArrayList<>();
        List<Token> commas = new ArrayList<>();
        // FuncRParams -> Exp { ',' Exp }
        params.add(parseExp());
        while(peek().getType() == TokenType.COMMA) {
            commas.add(matchAndConsume(TokenType.COMMA, 'z'));
            params.add(parseExp());
        }
        recorder.recordSyntax("FuncRParams");
        return new FuncRParamsNode(params, commas);
    }

    // --- 最终修正版：全面应用“解析一个，记录一次”模式 ---

    private MulExpNode parseMulExp() {
        List<UnaryExpNode> exps = new ArrayList<>();
        List<Token> ops = new ArrayList<>();
        exps.add(parseUnaryExp());
        recorder.recordSyntax("MulExp");
        while (peek().getType() == TokenType.MULT || peek().getType() == TokenType.DIV || peek().getType() == TokenType.MOD) {
            ops.add(consume());
            exps.add(parseUnaryExp());
            recorder.recordSyntax("MulExp");
        }
        return new MulExpNode(exps, ops);
    }

    private AddExpNode parseAddExp() {
        List<MulExpNode> exps = new ArrayList<>();
        List<Token> ops = new ArrayList<>();
        exps.add(parseMulExp());
        recorder.recordSyntax("AddExp");
        while (peek().getType() == TokenType.PLUS || peek().getType() == TokenType.MINU) {
            ops.add(consume());
            exps.add(parseMulExp());
            recorder.recordSyntax("AddExp");
        }
        return new AddExpNode(exps, ops);
    }

    private RelExpNode parseRelExp() {
        List<AddExpNode> exps = new ArrayList<>();
        List<Token> ops = new ArrayList<>();
        exps.add(parseAddExp());
        recorder.recordSyntax("RelExp");
        while (peek().getType() == TokenType.LSS || peek().getType() == TokenType.GRE ||
                peek().getType() == TokenType.LEQ || peek().getType() == TokenType.GEQ) {
            ops.add(consume());
            exps.add(parseAddExp());
            recorder.recordSyntax("RelExp");
        }
        return new RelExpNode(exps, ops);
    }

    private EqExpNode parseEqExp() {
        List<RelExpNode> exps = new ArrayList<>();
        List<Token> ops = new ArrayList<>();
        exps.add(parseRelExp());
        recorder.recordSyntax("EqExp");
        while (peek().getType() == TokenType.EQL || peek().getType() == TokenType.NEQ) {
            ops.add(consume());
            exps.add(parseRelExp());
            recorder.recordSyntax("EqExp");
        }
        return new EqExpNode(exps, ops);
    }

    private LAndExpNode parseLAndExp() {
        List<EqExpNode> exps = new ArrayList<>();
        List<Token> ops = new ArrayList<>();
        exps.add(parseEqExp());
        recorder.recordSyntax("LAndExp");
        while (peek().getType() == TokenType.AND) {
            ops.add(consume());
            exps.add(parseEqExp());
            recorder.recordSyntax("LAndExp");
        }
        return new LAndExpNode(exps, ops);
    }

    private LOrExpNode parseLOrExp() {
        List<LAndExpNode> exps = new ArrayList<>();
        List<Token> ops = new ArrayList<>();
        exps.add(parseLAndExp());
        recorder.recordSyntax("LOrExp");
        while (peek().getType() == TokenType.OR) {
            ops.add(consume());
            exps.add(parseLAndExp());
            recorder.recordSyntax("LOrExp");
        }
        return new LOrExpNode(exps, ops);
    }

}
