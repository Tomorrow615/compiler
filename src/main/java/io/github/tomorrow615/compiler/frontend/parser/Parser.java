package io.github.tomorrow615.compiler.frontend.parser;

import io.github.tomorrow615.compiler.frontend.lexer.Token;
import io.github.tomorrow615.compiler.frontend.lexer.TokenType;
import io.github.tomorrow615.compiler.frontend.ast.*;
import io.github.tomorrow615.compiler.frontend.ast.decl.*;
import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.ast.func.*;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int currentPos = 0; // 一个指向当前待处理 Token 的“指针”

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // "预读"：查看当前位置的Token，但不“吃掉”它。这是做语法判断的核心。
    private Token peek() {
        if (currentPos < tokens.size()) {
            return tokens.get(currentPos);
        }
        // 为了防止程序末尾出现数组越界，我们返回一个特殊的EOF Token
        // 假设你的Token构造函数支持这样创建
        return new Token(TokenType.EOF, "EOF", -1, -1);
    }

    // "消费"：吃掉当前的Token，并将指针后移一位。
    private Token consume() {
        if (currentPos < tokens.size()) {
            Token token = tokens.get(currentPos);
            currentPos++;
            return token;
        }
        return new Token(TokenType.EOF, "EOF", -1, -1);
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
                peek().getType() == TokenType.STATICTK || // <-- 增加对 static 的判断
                (peek().getType() == TokenType.INTTK && peekNext().getType() != TokenType.LPARENT && peekNext().getType() != TokenType.MAINTK)) {
            decls.add(parseDecl());
        }

        // 循环解析 {FuncDef}
        // 如何判断是FuncDef？预读一下，如果是'void'或者'int' (但后面不是'main')
        while (peek().getType() == TokenType.VOIDTK ||
                (peek().getType() == TokenType.INTTK && peekNext().getType() != TokenType.MAINTK)) {
            funcDefs.add(parseFuncDef());
        }

        // 解析 MainFuncDef
        mainFuncDef = parseMainFuncDef();

        return new CompUnitNode(decls, funcDefs, mainFuncDef, startLine);
    }

    // 预读第二个Token的辅助方法，对于判断`int`的类型非常有用
    private Token peekNext() {
        if (currentPos + 1 < tokens.size()) {
            return tokens.get(currentPos + 1);
        }
        return new Token(TokenType.EOF, "EOF", -1, -1);
    }

    private DeclNode parseDecl() {
        // 判断是常量声明还是变量声明
        if (peek().getType() == TokenType.CONSTTK) {
            return parseConstDecl();
        } else {
            return parseVarDecl();
        }
    }

    private ConstDeclNode parseConstDecl() {
        int startLine = peek().getLineNumber();

        // 1. 消费 'const' 关键字
        Token constToken = matchAndConsume(TokenType.CONSTTK);

        // 2. 解析 BType
        BTypeNode bType = parseBType();

        // 3. 解析第一个 ConstDef
        List<ConstDefNode> constDefs = new ArrayList<>();
        constDefs.add(parseConstDef());

        // 4. 循环解析 { ',' ConstDef }
        while (peek().getType() == TokenType.COMMA) {
            consume(); // 消费 ','
            constDefs.add(parseConstDef());
        }

        // 5. 匹配并消费 ';'
        Token semicn = matchAndConsume(TokenType.SEMICN);

        return new ConstDeclNode(constToken, bType, constDefs, semicn, startLine);
    }

    // 添加新的占位方法
    private BTypeNode parseBType() {
        // 根据文法，BType 必须是一个 'int'。
        // 我们直接调用 matchAndConsume 来确保这一点，并获取这个 Token。
        Token typeToken = matchAndConsume(TokenType.INTTK);

        // 用获取到的 Token 创建 BTypeNode 并返回。
        return new BTypeNode(typeToken);
    }

    private ConstDefNode parseConstDef() {
        // 1. 解析 Ident
        Token ident = matchAndConsume(TokenType.IDENFR);

        // 为数组维度信息准备列表
        List<Token> lBracks = new ArrayList<>();
        List<ConstExpNode> constExps = new ArrayList<>();
        List<Token> rBracks = new ArrayList<>();

        // 2. 使用 while 循环处理所有数组维度定义
        while (peek().getType() == TokenType.LBRACK) {
            lBracks.add(consume()); // 消费并保存 '['
            constExps.add(parseConstExp());
            rBracks.add(matchAndConsume(TokenType.RBRACK)); // 消费并保存 ']'
        }

        // 3. 解析 '='
        Token assignToken = matchAndConsume(TokenType.ASSIGN);

        // 4. 解析 ConstInitVal
        ConstInitValNode constInitVal = parseConstInitVal();

        return new ConstDefNode(ident, lBracks, constExps, rBracks, assignToken, constInitVal);
    }

    // 添加新的占位方法
    private ConstExpNode parseConstExp() {
        AddExpNode addExp = parseAddExp();
        return new ConstExpNode(addExp);
    }

    // 实现一个【简化版】的 AddExp 解析器，第三阶段再来完善
    private AddExpNode parseAddExp() {
        // TODO: Phase 3 will implement full expression parsing.
        // For now, we assume an AddExp is just a simple number for array dimensions.
        System.out.println("Parsing AddExp... (Simplified version)");

        // 暂时只处理最简单的情况：一个 IntConst
        // 后面我们会把它替换成完整的表达式解析逻辑
        Token number = matchAndConsume(TokenType.INTCON);

        // 为了让类型匹配，我们需要一个临时的AddExpNode来包装这个Number
        // 这是一个简化的设计，后续会重构
        return new AddExpNode(number);
    }

    private ConstInitValNode parseConstInitVal() {
        // 通过预读来判断是单个表达式还是数组初始化列表
        if (peek().getType() == TokenType.LBRACE) {
            // Case 2: Array Initializer -> '{' [ ConstExp { ',' ConstExp } ] '}'
            Token lBrace = consume(); // 消费并保存 '{'

            List<ConstExpNode> arrayInit = new ArrayList<>();
            List<Token> commas = new ArrayList<>();

            // 判断花括号内是否为空
            if (peek().getType() != TokenType.RBRACE) {
                arrayInit.add(parseConstExp());

                // 循环解析 { ',' ConstExp }
                while (peek().getType() == TokenType.COMMA) {
                    commas.add(consume()); // 消费并保存 ','
                    arrayInit.add(parseConstExp());
                }
            }

            Token rBrace = matchAndConsume(TokenType.RBRACE); // 消费并保存 '}'

            return new ConstInitValNode(lBrace, arrayInit, commas, rBrace);
        } else {
            // Case 1: Single Expression -> ConstExp
            ConstExpNode singleInit = parseConstExp();
            return new ConstInitValNode(singleInit);
        }
    }

    private VarDeclNode parseVarDecl() {
        // TODO: 在下一个任务中实现
        System.out.println("Parsing VarDecl... (Not implemented yet)");
        while(peek().getType() != TokenType.SEMICN && peek().getType() != TokenType.EOF) {
            consume();
        }
        consume(); // 消耗分号
        return null;
    }

    private FuncDefNode parseFuncDef() {
        // TODO: 后续任务中实现
        System.out.println("Parsing FuncDef... (Not implemented yet)");
        // 临时消耗掉一些token以防死循环，例如消耗到右花括号
        while(peek().getType() != TokenType.RBRACE && peek().getType() != TokenType.EOF) {
            consume();
        }
        consume(); // 消耗右花括号
        return null; // 暂时返回null
    }

    private MainFuncDefNode parseMainFuncDef() {
        // TODO: 后续任务中实现
        System.out.println("Parsing MainFuncDef... (Not implemented yet)");
        // 临时消耗掉一些token以防死循环，例如消耗到右花括号
        while(peek().getType() != TokenType.RBRACE && peek().getType() != TokenType.EOF) {
            consume();
        }
        consume(); // 消耗右花括号
        return new MainFuncDefNode(-1); // 暂时返回一个伪造的节点
    }

    private Token matchAndConsume(TokenType expectedType) {
        Token current = peek();
        if (current.getType() == expectedType) {
            return consume();
        } else {
            // 这里我们先简单地返回一个null，或者一个表示错误的Token
            // 后面可以集成ErrorReporter来报告错误
            System.err.println("Error: Expected " + expectedType + " but got " + current.getType() + " at line " + current.getLineNumber());
            // 错误恢复策略：暂时不消费，假装匹配到了，让上层继续
            return null;
        }
    }

}
