package io.github.tomorrow615.compiler.midend.irgen;

import io.github.tomorrow615.compiler.frontend.ast.ASTNode;
import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.ast.decl.*;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import io.github.tomorrow615.compiler.frontend.lexer.TokenType;
import io.github.tomorrow615.compiler.frontend.symbol.*;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.type.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.ArrayList;
import java.util.List;

public class ExpressionGenerator {
    private final IRGenContext context;
    private final IRBuilder builder;

    public ExpressionGenerator(IRGenContext context) {
        this.context = context;
        this.builder = context.getBuilder();
    }

    public Value visitExpression(ASTNode node) {
        if (node instanceof ExpNode) return visitExp((ExpNode) node);
        if (node instanceof AddExpNode) return visitAddExp((AddExpNode) node);
        if (node instanceof MulExpNode) return visitMulExp((MulExpNode) node);
        if (node instanceof UnaryExpNode) return visitUnaryExp((UnaryExpNode) node);
        if (node instanceof PrimaryExpNode) return visitPrimaryExp((PrimaryExpNode) node);
        if (node instanceof LValNode) return buildLValValue((LValNode) node);
        if (node instanceof NumberNode) return new ConstantInt(Integer.parseInt(((NumberNode) node).getIntConst().getText()));
        if (node instanceof RelExpNode) return visitRelExp((RelExpNode) node);
        if (node instanceof EqExpNode) return visitEqExp((EqExpNode) node);
        if (node instanceof LAndExpNode) return visitLAndExp((LAndExpNode) node);
        if (node instanceof LOrExpNode) return visitLOrExp((LOrExpNode) node);
        return null;
    }

    public Value visitInitVal(InitValNode node) {
        if (node != null && node.getType() == InitValNode.Type.SINGLE) {
            return visitExpression(node.getSingleInit());
        }
        return null;
    }

    public Value visitConstInitVal(ConstInitValNode node) {
        if (node != null && node.getType() == ConstInitValNode.Type.SINGLE) {
            return visitConstExp(node.getSingleInit());
        }
        return null;
    }

    public Value visitConstExp(ConstExpNode node) {
        return node == null ? null : visitAddExp(node.getAddExp());
    }

    public Value visitExp(ExpNode node) {
        return node == null ? null : visitAddExp(node.getAddExp());
    }

    public Value visitCond(CondNode node) {
        return node == null ? null : visitLOrExp(node.getLorExp());
    }

    public Value visitPrimaryExp(PrimaryExpNode node) {
        if (node == null) return null;
        return switch (node.getType()) {
            case LVAL -> buildLValValue(node.getLval());
            case NUMBER -> new ConstantInt(Integer.parseInt(node.getNumber().getIntConst().getText()));
            case PAREN_EXP -> visitExp(node.getExp());
        };
    }

    public Value visitUnaryExp(UnaryExpNode node) {
        if (node == null) return null;
        switch (node.getType()) {
            case PRIMARY:
                return visitPrimaryExp(node.getPrimaryExp());
            case UNARY_OP:
                Value val = visitUnaryExp(node.getUnaryExp());
                Token op = node.getUnaryOp().getOp();
                if (op.getType() == TokenType.MINU) {
                    return builder.createSub(new ConstantInt(0), val, "negtmp");
                } else if (op.getType() == TokenType.PLUS) {
                    return val;
                } else if (op.getType() == TokenType.NOT) {
                    Value i1Val = builder.createIcmp(IcmpInst.CmpType.EQ, val, new ConstantInt(0), "nottmp");
                    return builder.createZext(i1Val, IntegerType.i32, "zexttmp");
                }
                break;
            case FUNC_CALL:
                return handleFuncCall(node);
        }
        return null;
    }

    private Value handleFuncCall(UnaryExpNode node) {
        Token ident = node.getIdent();
        Symbol symbol = context.getCurrentScope().lookup(ident.getText());
        if (!(symbol instanceof FuncSymbol funcSymbol)) return null;

        Function callee = (Function) funcSymbol.getLlvmValue();
        List<Value> args = new ArrayList<>();
        List<ValueSymbol> expectedParams = funcSymbol.getParameters();

        if (node.getFuncRParams() != null) {
            List<ExpNode> actualParams = node.getFuncRParams().getParams();
            for (int i = 0; i < actualParams.size(); i++) {
                ExpNode argExp = actualParams.get(i);
                ValueSymbol expectedParam = expectedParams.get(i);
                if (expectedParam.getDimension() > 0) {
                    handleArrayParam(argExp, args);
                } else {
                    args.add(visitExp(argExp));
                }
            }
        }
        String callName = callee.getReturnType().isVoidType() ? "" : ident.getText() + ".call";
        return builder.createCall(callee, args, callName);
    }

    private void handleArrayParam(ExpNode argExp, List<Value> args) {
        LValNode lvalNode = null;
        try {
            lvalNode = ((PrimaryExpNode) ((UnaryExpNode) ((MulExpNode) argExp.getAddExp().getMulExps().get(0)).getUnaryExps().get(0)).getPrimaryExp()).getLval();
        } catch (Exception e) { /* ignore */ }

        if (lvalNode != null && lvalNode.getType() == LValNode.Type.SCALAR) {
            Value basePtr = buildLValPointer(lvalNode);
            Type basePtrType = basePtr.getType();
            if (basePtrType instanceof PointerType pt) {
                Type targetType = pt.getTargetType();
                if (targetType.isArrayType()) {
                    Value zero = new ConstantInt(0);
                    args.add(builder.createGep(basePtr, List.of(zero, zero), "arr.decay"));
                } else if (targetType.isPointerType()) {
                    args.add(builder.createLoad(basePtr, "arr.param.decay"));
                }
            }
        } else {
            args.add(visitExp(argExp));
        }
    }

    public Value visitMulExp(MulExpNode node) {
        Value lhs = visitUnaryExp(node.getUnaryExps().get(0));
        for (int i = 0; i < node.getOperators().size(); i++) {
            Value rhs = visitUnaryExp(node.getUnaryExps().get(i + 1));
            switch (node.getOperators().get(i).getType()) {
                case MULT -> lhs = builder.createMul(lhs, rhs, "multmp");
                case DIV -> lhs = builder.createSdiv(lhs, rhs, "divtmp");
                case MOD -> lhs = builder.createSrem(lhs, rhs, "modtmp");
            }
        }
        return lhs;
    }

    public Value visitAddExp(AddExpNode node) {
        Value lhs = visitMulExp(node.getMulExps().get(0));
        for (int i = 0; i < node.getOperators().size(); i++) {
            Value rhs = visitMulExp(node.getMulExps().get(i + 1));
            switch (node.getOperators().get(i).getType()) {
                case PLUS -> lhs = builder.createAdd(lhs, rhs, "addtmp");
                case MINU -> lhs = builder.createSub(lhs, rhs, "subtmp");
            }
        }
        return lhs;
    }

    public Value visitRelExp(RelExpNode node) {
        Value lhs = visitAddExp(node.getAddExps().get(0));
        for (int i = 0; i < node.getOperators().size(); i++) {
            Value rhs = visitAddExp(node.getAddExps().get(i + 1));
            Value i1Val = switch (node.getOperators().get(i).getType()) {
                case LSS -> builder.createIcmp(IcmpInst.CmpType.SLT, lhs, rhs, "cmptmp");
                case LEQ -> builder.createIcmp(IcmpInst.CmpType.SLE, lhs, rhs, "cmptmp");
                case GRE -> builder.createIcmp(IcmpInst.CmpType.SGT, lhs, rhs, "cmptmp");
                case GEQ -> builder.createIcmp(IcmpInst.CmpType.SGE, lhs, rhs, "cmptmp");
                default -> null;
            };
            lhs = builder.createZext(i1Val, IntegerType.i32, "zexttmp");
        }
        return lhs;
    }

    public Value visitEqExp(EqExpNode node) {
        Value lhs = visitRelExp(node.getRelExps().get(0));
        for (int i = 0; i < node.getOperators().size(); i++) {
            Value rhs = visitRelExp(node.getRelExps().get(i + 1));
            Value i1Val = switch (node.getOperators().get(i).getType()) {
                case EQL -> builder.createIcmp(IcmpInst.CmpType.EQ, lhs, rhs, "cmptmp");
                case NEQ -> builder.createIcmp(IcmpInst.CmpType.NE, lhs, rhs, "cmptmp");
                default -> null;
            };
            lhs = builder.createZext(i1Val, IntegerType.i32, "zexttmp");
        }
        return lhs;
    }

    public Value visitLAndExp(LAndExpNode node) {
        if (node.getOperators().isEmpty()) return visitEqExp(node.getEqExps().get(0));

        Function currentFunc = context.getCurrentFunction();
        BasicBlock trueBB = new BasicBlock(context.getNextLabel("land.true"), currentFunc);
        BasicBlock falseBB = new BasicBlock(context.getNextLabel("land.false"), currentFunc);
        BasicBlock mergeBB = new BasicBlock(context.getNextLabel("land.merge"), currentFunc);

        buildAndBranch(node, trueBB, falseBB);

        builder.setInsertPoint(trueBB);
        builder.createBr(mergeBB);
        builder.setInsertPoint(falseBB);
        builder.createBr(mergeBB);

        builder.setInsertPoint(mergeBB);
        PhiInst phi = builder.createPhi(IntegerType.i1, "land.res");
        phi.addIncoming(new ConstantInt(1, IntegerType.i1), trueBB);
        phi.addIncoming(new ConstantInt(0, IntegerType.i1), falseBB);
        return builder.createZext(phi, IntegerType.i32, "land.val");
    }

    public Value visitLOrExp(LOrExpNode node) {
        if (node.getOperators().isEmpty()) return visitLAndExp(node.getlAndExps().get(0));

        Function currentFunc = context.getCurrentFunction();
        BasicBlock trueBB = new BasicBlock(context.getNextLabel("lor.true"), currentFunc);
        BasicBlock falseBB = new BasicBlock(context.getNextLabel("lor.false"), currentFunc);
        BasicBlock mergeBB = new BasicBlock(context.getNextLabel("lor.merge"), currentFunc);

        buildOrBranch(node, trueBB, falseBB);

        builder.setInsertPoint(trueBB);
        builder.createBr(mergeBB);
        builder.setInsertPoint(falseBB);
        builder.createBr(mergeBB);

        builder.setInsertPoint(mergeBB);
        PhiInst phi = builder.createPhi(IntegerType.i1, "lor.res");
        phi.addIncoming(new ConstantInt(1, IntegerType.i1), trueBB);
        phi.addIncoming(new ConstantInt(0, IntegerType.i1), falseBB);
        return builder.createZext(phi, IntegerType.i32, "lor.val");
    }

    // ==================== 指针与分支生成 ====================

    public Value buildLValPointer(LValNode node) {
        Symbol symbol = context.getCurrentScope().lookup(node.getIdent().getText());
        if (!(symbol instanceof ValueSymbol valueSymbol)) return null;

        Value basePtr = valueSymbol.getLlvmValue();
        if (node.getType() == LValNode.Type.SCALAR) {
            return basePtr;
        } else {
            Value index = visitExpression(node.getArrayExps().get(0));
            Type basePtrType = basePtr.getType();
            if (basePtrType instanceof PointerType pt) {
                Type targetType = pt.getTargetType();
                if (targetType.isArrayType()) {
                    return builder.createGep(basePtr, List.of(new ConstantInt(0), index), "arr.idx");
                } else if (targetType.isPointerType()) {
                    Value loadedPtr = builder.createLoad(basePtr, "arr.param.ptr");
                    return builder.createGep(loadedPtr, List.of(index), "arr.param.idx");
                }
            }
            return null;
        }
    }

    public Value buildLValValue(LValNode node) {
        Value ptr = buildLValPointer(node);
        return builder.createLoad(ptr, node.getIdent().getText() + ".val");
    }

    public void buildConditionBranch(ASTNode node, BasicBlock trueBB, BasicBlock falseBB) {
        if (node instanceof CondNode c) {
            buildConditionBranch(c.getLorExp(), trueBB, falseBB);
        } else if (node instanceof LOrExpNode lor) {
            buildOrBranch(lor, trueBB, falseBB);
        } else if (node instanceof LAndExpNode land) {
            buildAndBranch(land, trueBB, falseBB);
        } else {
            Value val = visitExpression(node);
            Value condVal = (val.getType() == IntegerType.i1) ? val
                    : builder.createIcmp(IcmpInst.CmpType.NE, val, new ConstantInt(0), "cond");
            builder.createCondBr(condVal, trueBB, falseBB);
        }
    }

    private void buildOrBranch(LOrExpNode node, BasicBlock trueBB, BasicBlock falseBB) {
        List<LAndExpNode> parts = node.getlAndExps();
        for (int i = 0; i < parts.size() - 1; i++) {
            BasicBlock nextBB = new BasicBlock(context.getNextLabel("lor.next"), context.getCurrentFunction());
            buildConditionBranch(parts.get(i), trueBB, nextBB);
            builder.setInsertPoint(nextBB);
        }
        buildConditionBranch(parts.get(parts.size() - 1), trueBB, falseBB);
    }

    private void buildAndBranch(LAndExpNode node, BasicBlock trueBB, BasicBlock falseBB) {
        List<EqExpNode> parts = node.getEqExps();
        for (int i = 0; i < parts.size() - 1; i++) {
            BasicBlock nextBB = new BasicBlock(context.getNextLabel("land.next"), context.getCurrentFunction());
            buildConditionBranch(parts.get(i), nextBB, falseBB);
            builder.setInsertPoint(nextBB);
        }
        buildConditionBranch(parts.get(parts.size() - 1), trueBB, falseBB);
    }
}