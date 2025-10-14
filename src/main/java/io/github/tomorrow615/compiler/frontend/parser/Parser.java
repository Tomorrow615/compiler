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

    private BlockItemNode parseBlockItem() {
        // 根据修正后的文法，Decl 的起始符号包括 'const', 'static', 'int'
        if (peek().getType() == TokenType.CONSTTK ||
                peek().getType() == TokenType.STATICTK ||
                peek().getType() == TokenType.INTTK) {
            return parseDecl();
        } else {
            return parseStmt();
        }
    }

    private StmtNode parseStmt() {
        // 根据文法，查看 Stmt 的各个产生式开头的 Token
        TokenType currentType = peek().getType();

        if (currentType == TokenType.LBRACE) {
            // Stmt -> Block
            return parseBlock();
        } else if (currentType == TokenType.IFTK) {
            // Stmt -> 'if' ...
            return parseIfStmt();
        } else if (currentType == TokenType.FORTK) {
            // Stmt -> 'for' ...
            return parseForStmt();
        } else if (currentType == TokenType.BREAKTK) {
            // Stmt -> 'break' ...
            return parseBreakStmt();
        } else if (currentType == TokenType.CONTINUETK) {
            // Stmt -> 'continue' ...
            return parseContinueStmt();
        } else if (currentType == TokenType.RETURNTK) {
            // Stmt -> 'return' ...
            return parseReturnStmt();
        } else if (currentType == TokenType.PRINTFTK) {
            // Stmt -> 'printf' ...
            return parsePrintfStmt();
        } else {
            // 剩下的情况是 LVal = Exp; 或 [Exp];
            // 这是最复杂的情况，因为它们都可能以 IDENFR 开头
            // 我们创建一个专门的方法来处理
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

    private StmtNode parseAssignOrExpStmt() {
        // 关键的 "试探-回退" 逻辑
        // 1. 保存当前位置
        int savedPos = savePosition();

        // 2. 试探性地解析 LVal
        // 为了避免试探时产生错误报告，我们暂时不处理这里的错误
        // (一个更复杂的实现会创建一个不报告错误的 " speculative parser")
        try {
            LValNode lVal = parseLVal(true);
            if (peek().getType() == TokenType.ASSIGN) {
                // 试探成功，我们确认这就是一个赋值语句
                // 此时 lVal 已经被解析，但还没有打印 <LVal>，现在补上
                recorder.recordSyntax("LVal");
                Token assignToken = matchAndConsume(TokenType.ASSIGN, 'z');
                ExpNode exp = parseExp();
                Token semicn = matchAndConsume(TokenType.SEMICN, 'i');
                recorder.recordSyntax("Stmt");
                return new AssignStmtNode(lVal, assignToken, exp, semicn);
            }
        } catch (Exception e) {
            // 如果试探性解析 LVal 失败（例如，它实际上是 `(a+b)`），
            // 也会进入回退逻辑。
        }

        // 3b. 试探失败，它不是一个以 LVal 开头的赋值语句。
        // 我们回退到保存的位置，把它当作一个普通的表达式语句来解析。
        restorePosition(savedPos);

        ExpNode exp = null;
        if (peek().getType() != TokenType.SEMICN) {
            // 对应 [Exp]; 中的 Exp
            exp = parseExp();
        }
        // 对应 ';'
        Token semicn = matchAndConsume(TokenType.SEMICN, 'i');
        recorder.recordSyntax("Stmt");
        return new ExpStmtNode(exp, semicn);
    }

    private CondNode parseCond() {
        // 文法: Cond -> LOrExp。LOrExp 是 AddExp 的上层，所以最终是 Exp。
        // 在第三阶段实现完整表达式解析前，我们暂时直接调用 parseExp()。
        ExpNode exp = parseExp();
        recorder.recordSyntax("Cond");
        return new CondNode(exp);
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

}
