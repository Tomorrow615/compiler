package io.github.tomorrow615.compiler.frontend.parser;

import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import io.github.tomorrow615.compiler.frontend.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

public class ExpressionParser {
    private final Parser mainParser;

    public ExpressionParser(Parser mainParser) {
        this.mainParser = mainParser;
    }

    // 常量表达式 ConstExp → AddExp 注：使用的 Ident 必须是常量
    public ConstExpNode parseConstExp() {
        AddExpNode addExp = parseAddExp();
        mainParser.getRecorder().recordSyntax("ConstExp");
        return new ConstExpNode(addExp);
    }

    // 表达式 Exp → AddExp
    public ExpNode parseExp() {
        AddExpNode addExp = parseAddExp();
        mainParser.getRecorder().recordSyntax("Exp");
        return new ExpNode(addExp);
    }

    // 条件表达式 Cond → LOrExp
    public CondNode parseCond() {
        LOrExpNode lorExp = parseLOrExp();
        mainParser.getRecorder().recordSyntax("Cond");
        return new CondNode(lorExp);
    }

    // 数值 Number → IntConst
    private NumberNode parseNumber() {
        Token numberToken = mainParser.consume();
        NumberNode numberNode = new NumberNode(numberToken);
        mainParser.getRecorder().recordSyntax("Number");
        return numberNode;
    }

    // 单目运算符 UnaryOp → '+' | '−' | '!' 注：'!'仅出现在条件表达式中
    private UnaryOpNode parseUnaryOp() {
        Token opToken = mainParser.consume();
        UnaryOpNode opNode = new UnaryOpNode(opToken);
        mainParser.getRecorder().recordSyntax("UnaryOp");
        return opNode;
    }

    // 基本表达式 PrimaryExp → '(' Exp ')' | LVal | Number // j
    public PrimaryExpNode parsePrimaryExp() {
        PrimaryExpNode node;
        if (mainParser.peek().getType() == TokenType.LPARENT) {
            mainParser.consume();
            ExpNode exp = parseExp();
            mainParser.matchAndConsume(TokenType.RPARENT, 'j');
            node = new PrimaryExpNode(exp);
        } else if (mainParser.peek().getType() == TokenType.IDENFR) {
            LValNode lVal = mainParser.parseLVal();
            node = new PrimaryExpNode(lVal);
        } else {
            NumberNode numberNode = this.parseNumber();
            node = new PrimaryExpNode(numberNode);
        }
        mainParser.getRecorder().recordSyntax("PrimaryExp");
        return node;
    }

    // 一元表达式 UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp // j
    public UnaryExpNode parseUnaryExp() {
        UnaryExpNode node;
        if (mainParser.peek().getType() == TokenType.PLUS ||
                mainParser.peek().getType() == TokenType.MINU ||
                mainParser.peek().getType() == TokenType.NOT) {
            UnaryOpNode opNode = this.parseUnaryOp();
            UnaryExpNode exp = parseUnaryExp();
            node = new UnaryExpNode(opNode, exp);
        } else if (mainParser.peek().getType() == TokenType.IDENFR &&
                mainParser.peekNext().getType() == TokenType.LPARENT) {
            Token ident = mainParser.consume();
            mainParser.consume();
            FuncRParamsNode params = null;
            if (mainParser.peek().getType() != TokenType.RPARENT) {
                params = parseFuncRParams();
            }
            mainParser.matchAndConsume(TokenType.RPARENT, 'j');
            node = new UnaryExpNode(ident, params);
        } else {
            PrimaryExpNode primaryExp = parsePrimaryExp();
            node = new UnaryExpNode(primaryExp);
        }
        mainParser.getRecorder().recordSyntax("UnaryExp");
        return node;
    }

    // 函数实参表 FuncRParams → Exp { ',' Exp }
    public FuncRParamsNode parseFuncRParams() {
        List<ExpNode> params = new ArrayList<>();
        params.add(parseExp());
        while (mainParser.peek().getType() == TokenType.COMMA) {
            mainParser.consume();
            params.add(parseExp());
        }
        mainParser.getRecorder().recordSyntax("FuncRParams");
        return new FuncRParamsNode(params);
    }

    // 乘除模表达式 MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
    public MulExpNode parseMulExp() {
        List<UnaryExpNode> exps = new ArrayList<>();
        List<Token> ops = new ArrayList<>();
        exps.add(parseUnaryExp());
        mainParser.getRecorder().recordSyntax("MulExp");
        while (mainParser.peek().getType() == TokenType.MULT ||
                mainParser.peek().getType() == TokenType.DIV ||
                mainParser.peek().getType() == TokenType.MOD) {
            ops.add(mainParser.consume());
            exps.add(parseUnaryExp());
            mainParser.getRecorder().recordSyntax("MulExp");
        }
        return new MulExpNode(exps, ops);
    }

    // 加减表达式 AddExp → MulExp | AddExp ('+' | '−') MulExp
    public AddExpNode parseAddExp() {
        List<MulExpNode> exps = new ArrayList<>();
        List<Token> ops = new ArrayList<>();
        exps.add(parseMulExp());
        mainParser.getRecorder().recordSyntax("AddExp");
        while (mainParser.peek().getType() == TokenType.PLUS ||
                mainParser.peek().getType() == TokenType.MINU) {
            ops.add(mainParser.consume());
            exps.add(parseMulExp());
            mainParser.getRecorder().recordSyntax("AddExp");
        }
        return new AddExpNode(exps, ops);
    }

    // 关系表达式 RelExp → AddExp | RelExp ('<' | '>' | '<=' | '>=') AddExp
    public RelExpNode parseRelExp() {
        List<AddExpNode> exps = new ArrayList<>();
        List<Token> ops = new ArrayList<>();
        exps.add(parseAddExp());
        mainParser.getRecorder().recordSyntax("RelExp");
        while (mainParser.peek().getType() == TokenType.LSS ||
                mainParser.peek().getType() == TokenType.GRE ||
                mainParser.peek().getType() == TokenType.LEQ ||
                mainParser.peek().getType() == TokenType.GEQ) {
            ops.add(mainParser.consume());
            exps.add(parseAddExp());
            mainParser.getRecorder().recordSyntax("RelExp");
        }
        return new RelExpNode(exps, ops);
    }

    // 相等性表达式 EqExp → RelExp | EqExp ('==' | '!=') RelExp
    public EqExpNode parseEqExp() {
        List<RelExpNode> exps = new ArrayList<>();
        List<Token> ops = new ArrayList<>();
        exps.add(parseRelExp());
        mainParser.getRecorder().recordSyntax("EqExp");
        while (mainParser.peek().getType() == TokenType.EQL
                || mainParser.peek().getType() == TokenType.NEQ) {
            ops.add(mainParser.consume());
            exps.add(parseRelExp());
            mainParser.getRecorder().recordSyntax("EqExp");
        }
        return new EqExpNode(exps, ops);
    }

    // 逻辑与表达式 LAndExp → EqExp | LAndExp '&&' EqExp
    public LAndExpNode parseLAndExp() {
        List<EqExpNode> exps = new ArrayList<>();
        List<Token> ops = new ArrayList<>();
        exps.add(parseEqExp());
        mainParser.getRecorder().recordSyntax("LAndExp");
        while (mainParser.peek().getType() == TokenType.AND) {
            ops.add(mainParser.consume());
            exps.add(parseEqExp());
            mainParser.getRecorder().recordSyntax("LAndExp");
        }
        return new LAndExpNode(exps, ops);
    }

    // 逻辑或表达式 LOrExp → LAndExp | LOrExp '||' LAndExp
    public LOrExpNode parseLOrExp() {
        List<LAndExpNode> exps = new ArrayList<>();
        List<Token> ops = new ArrayList<>();
        exps.add(parseLAndExp());
        mainParser.getRecorder().recordSyntax("LOrExp");
        while (mainParser.peek().getType() == TokenType.OR) {
            ops.add(mainParser.consume());
            exps.add(parseLAndExp());
            mainParser.getRecorder().recordSyntax("LOrExp");
        }
        return new LOrExpNode(exps, ops);
    }

}