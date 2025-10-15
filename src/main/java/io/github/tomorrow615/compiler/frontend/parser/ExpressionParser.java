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

    // +++ 新增移动过来的方法 +++
    public ConstExpNode parseConstExp() {
        // 这个方法内部调用了 parseAddExp()，现在它们同在一个类中，调用关系正确了
        AddExpNode addExp = parseAddExp();
        mainParser.getRecorder().recordSyntax("ConstExp");
        return new ConstExpNode(addExp);
    }

    public ExpNode parseExp() {
        AddExpNode addExp = parseAddExp();
        mainParser.getRecorder().recordSyntax("Exp");
        return new ExpNode(addExp);
    }

    public CondNode parseCond() {
        LOrExpNode lorExp = parseLOrExp();
        mainParser.getRecorder().recordSyntax("Cond");
        return new CondNode(lorExp);
    }

    public PrimaryExpNode parsePrimaryExp() {
        PrimaryExpNode node;
        if (mainParser.peek().getType() == TokenType.LPARENT) {
            mainParser.matchAndConsume(TokenType.LPARENT, 'j');
            ExpNode exp = parseExp();
            mainParser.matchAndConsume(TokenType.RPARENT, 'j');
            node = new PrimaryExpNode(exp);
        } else if (mainParser.peek().getType() == TokenType.IDENFR) {
            LValNode lVal = mainParser.parseLVal(false);
            node = new PrimaryExpNode(lVal);
        } else { // Number
            Token numberToken = mainParser.matchAndConsume(TokenType.INTCON, 'z');
            NumberNode numberNode = new NumberNode(numberToken);
            mainParser.getRecorder().recordSyntax("Number");
            node = new PrimaryExpNode(numberNode);
        }
        mainParser.getRecorder().recordSyntax("PrimaryExp");
        return node;
    }

    public UnaryExpNode parseUnaryExp() {
        UnaryExpNode node;
        if (mainParser.peek().getType() == TokenType.PLUS || mainParser.peek().getType() == TokenType.MINU || mainParser.peek().getType() == TokenType.NOT) {
            Token opToken = mainParser.consume();
            UnaryOpNode opNode = new UnaryOpNode(opToken);
            mainParser.getRecorder().recordSyntax("UnaryOp");
            UnaryExpNode exp = parseUnaryExp();
            node = new UnaryExpNode(opNode, exp);
        } else if (mainParser.peek().getType() == TokenType.IDENFR && mainParser.peekNext().getType() == TokenType.LPARENT) {
            Token ident = mainParser.matchAndConsume(TokenType.IDENFR, 'z');
            mainParser.matchAndConsume(TokenType.LPARENT, 'j');
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

    public FuncRParamsNode parseFuncRParams() {
        List<ExpNode> params = new ArrayList<>();
        List<Token> commas = new ArrayList<>();
        params.add(parseExp());
        while (mainParser.peek().getType() == TokenType.COMMA) {
            commas.add(mainParser.matchAndConsume(TokenType.COMMA, 'z'));
            params.add(parseExp());
        }
        mainParser.getRecorder().recordSyntax("FuncRParams");
        return new FuncRParamsNode(params, commas);
    }

    public MulExpNode parseMulExp() {
        List<UnaryExpNode> exps = new ArrayList<>();
        List<Token> ops = new ArrayList<>();
        exps.add(parseUnaryExp());
        mainParser.getRecorder().recordSyntax("MulExp");
        while (mainParser.peek().getType() == TokenType.MULT || mainParser.peek().getType() == TokenType.DIV || mainParser.peek().getType() == TokenType.MOD) {
            ops.add(mainParser.consume());
            exps.add(parseUnaryExp());
            mainParser.getRecorder().recordSyntax("MulExp");
        }
        return new MulExpNode(exps, ops);
    }

    public AddExpNode parseAddExp() {
        List<MulExpNode> exps = new ArrayList<>();
        List<Token> ops = new ArrayList<>();
        exps.add(parseMulExp());
        mainParser.getRecorder().recordSyntax("AddExp");
        while (mainParser.peek().getType() == TokenType.PLUS || mainParser.peek().getType() == TokenType.MINU) {
            ops.add(mainParser.consume());
            exps.add(parseMulExp());
            mainParser.getRecorder().recordSyntax("AddExp");
        }
        return new AddExpNode(exps, ops);
    }

    public RelExpNode parseRelExp() {
        List<AddExpNode> exps = new ArrayList<>();
        List<Token> ops = new ArrayList<>();
        exps.add(parseAddExp());
        mainParser.getRecorder().recordSyntax("RelExp");
        while (mainParser.peek().getType() == TokenType.LSS || mainParser.peek().getType() == TokenType.GRE ||
                mainParser.peek().getType() == TokenType.LEQ || mainParser.peek().getType() == TokenType.GEQ) {
            ops.add(mainParser.consume());
            exps.add(parseAddExp());
            mainParser.getRecorder().recordSyntax("RelExp");
        }
        return new RelExpNode(exps, ops);
    }

    public EqExpNode parseEqExp() {
        List<RelExpNode> exps = new ArrayList<>();
        List<Token> ops = new ArrayList<>();
        exps.add(parseRelExp());
        mainParser.getRecorder().recordSyntax("EqExp");
        while (mainParser.peek().getType() == TokenType.EQL || mainParser.peek().getType() == TokenType.NEQ) {
            ops.add(mainParser.consume());
            exps.add(parseRelExp());
            mainParser.getRecorder().recordSyntax("EqExp");
        }
        return new EqExpNode(exps, ops);
    }

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