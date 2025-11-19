package io.github.tomorrow615.compiler.midend.irgen;

import io.github.tomorrow615.compiler.frontend.ast.*;
import io.github.tomorrow615.compiler.frontend.ast.decl.*;
import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import io.github.tomorrow615.compiler.frontend.lexer.TokenType;
import io.github.tomorrow615.compiler.frontend.symbol.*;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.type.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.List;
import java.util.ArrayList;

public class ExpressionIRVisitor {
    private final IRGeneratorVisitor hub;
    private final IRBuilder builder;

    public ExpressionIRVisitor(IRGeneratorVisitor hub, IRBuilder builder) {
        this.hub = hub;
        this.builder = builder;
    }

    public Value visitExpression(ASTNode node) {
        if (node instanceof ExpNode) return visitExp((ExpNode) node);
        if (node instanceof AddExpNode) return visitAddExp((AddExpNode) node);
        if (node instanceof MulExpNode) return visitMulExp((MulExpNode) node);
        if (node instanceof UnaryExpNode) return visitUnaryExp((UnaryExpNode) node);
        if (node instanceof PrimaryExpNode) return visitPrimaryExp((PrimaryExpNode) node);
        if (node instanceof LValNode) return buildLValValue((LValNode) node); // 明确意图：获取值
        if (node instanceof NumberNode) return new ConstantInt(Integer.parseInt(((NumberNode) node).getIntConst().getText()));
        if (node instanceof RelExpNode) return visitRelExp((RelExpNode) node);
        if (node instanceof EqExpNode) return visitEqExp((EqExpNode) node);
        if (node instanceof LAndExpNode) return visitLAndExp((LAndExpNode) node);
        if (node instanceof LOrExpNode) return visitLOrExp((LOrExpNode) node);
        return null;
    }

    // ==========================================
    //          初始化相关 (InitVal)
    // ==========================================

    public Value visitInitVal(InitValNode node) {
        if (node == null) return null;
        if (node.getType() == InitValNode.Type.SINGLE) {
            return visitExpression(node.getSingleInit());
        }
        return null; // 暂不支持数组
    }

    public Value visitConstInitVal(ConstInitValNode node) {
        if (node == null) return null;
        if (node.getType() == ConstInitValNode.Type.SINGLE) {
            return visitConstExp(node.getSingleInit());
        }
        return null; // 暂不支持数组
    }

    // ==========================================
    //          数值计算 (Return Value)
    // ==========================================

    public Value visitConstExp(ConstExpNode node) {
        if (node == null) return null;
        return visitAddExp(node.getAddExp());
    }

    public Value visitExp(ExpNode node) {
        if (node == null) return null;
        return visitAddExp(node.getAddExp());
    }

    public Value visitCond(CondNode node) {
        if (node == null) return null;
        return visitLOrExp(node.getLorExp());
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
                Value val = visitUnaryExp(node.getUnaryExp()); // 递归调用显式方法
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
                return handleFuncCall(node); // 抽取逻辑，保持 visit 方法整洁
        }
        return null;
    }

    // 将复杂的函数调用逻辑抽取出来，保持 visitUnaryExp 清晰
    private Value handleFuncCall(UnaryExpNode node) {
        Token ident = node.getIdent();
        Symbol symbol = hub.getCurrentScope().lookup(ident.getText());
        if (!(symbol instanceof FuncSymbol funcSymbol)) {
            return null;
        }

        Function callee = (Function) funcSymbol.getLlvmValue();
        List<Value> args = new ArrayList<>();
        List<ValueSymbol> expectedParams = funcSymbol.getParameters();

        if (node.getFuncRParams() != null) {
            List<ExpNode> actualParams = node.getFuncRParams().getParams();

            for (int i = 0; i < actualParams.size(); i++) {
                ExpNode argExp = actualParams.get(i);
                ValueSymbol expectedParam = expectedParams.get(i);

                if (expectedParam.getDimension() > 0) {
                    // 期望数组 (i32*)，处理指针衰变逻辑
                    handleArrayParam(argExp, args);
                } else {
                    // 期望标量 (i32)
                    args.add(visitExp(argExp));
                }
            }
        }

        String callName = (callee.getReturnType().isVoidType()) ? "" : ident.getText() + ".call";
        return builder.createCall(callee, args, callName);
    }

    private void handleArrayParam(ExpNode argExp, List<Value> args) {
        LValNode lvalNode = null;
        try {
            // 尝试解析 Exp -> ... -> LVal
            lvalNode = ((PrimaryExpNode) ((UnaryExpNode) ((MulExpNode) argExp.getAddExp().getMulExps().get(0)).getUnaryExps().get(0)).getPrimaryExp()).getLval();
        } catch (Exception e) { /* 忽略 */ }

        if (lvalNode != null && lvalNode.getType() == LValNode.Type.SCALAR) {
            Value basePtr = buildLValPointer(lvalNode); // 获取地址
            Type basePtrType = basePtr.getType();
            if (basePtrType instanceof PointerType) {
                Type targetType = ((PointerType) basePtrType).getTargetType();
                if (targetType.isArrayType()) {
                    Value zero = new ConstantInt(0);
                    Value decayedPtr = builder.createGep(basePtr, List.of(zero, zero), "arr.decay");
                    args.add(decayedPtr);
                } else if (targetType.isPointerType()) {
                    Value loadedPtr = builder.createLoad(basePtr, "arr.param.decay");
                    args.add(loadedPtr);
                }
            }
        } else {
            args.add(visitExp(argExp)); // 错误回退
        }
    }

    public Value visitMulExp(MulExpNode node) {
        if (node == null) return null;
        Value lhs = visitUnaryExp(node.getUnaryExps().get(0));
        for (int i = 0; i < node.getOperators().size(); i++) {
            Token op = node.getOperators().get(i);
            Value rhs = visitUnaryExp(node.getUnaryExps().get(i + 1));
            switch (op.getType()) {
                case MULT -> lhs = builder.createMul(lhs, rhs, "multmp");
                case DIV -> lhs = builder.createSdiv(lhs, rhs, "divtmp");
                case MOD -> lhs = builder.createSrem(lhs, rhs, "modtmp");
            }
        }
        return lhs;
    }

    public Value visitAddExp(AddExpNode node) {
        if (node == null) return null;
        Value lhs = visitMulExp(node.getMulExps().get(0));
        for (int i = 0; i < node.getOperators().size(); i++) {
            Token op = node.getOperators().get(i);
            Value rhs = visitMulExp(node.getMulExps().get(i + 1));
            switch (op.getType()) {
                case PLUS -> lhs = builder.createAdd(lhs, rhs, "addtmp");
                case MINU -> lhs = builder.createSub(lhs, rhs, "subtmp");
            }
        }
        return lhs;
    }

    public Value visitRelExp(RelExpNode node) {
        if (node == null) return null;
        Value lhs_i32 = visitAddExp(node.getAddExps().get(0));
        for (int i = 0; i < node.getOperators().size(); i++) {
            Token op = node.getOperators().get(i);
            Value rhs_i32 = visitAddExp(node.getAddExps().get(i + 1));
            Value i1Val;
            switch (op.getType()) {
                case LSS -> i1Val = builder.createIcmp(IcmpInst.CmpType.SLT, lhs_i32, rhs_i32, "cmptmp");
                case LEQ -> i1Val = builder.createIcmp(IcmpInst.CmpType.SLE, lhs_i32, rhs_i32, "cmptmp");
                case GRE -> i1Val = builder.createIcmp(IcmpInst.CmpType.SGT, lhs_i32, rhs_i32, "cmptmp");
                case GEQ -> i1Val = builder.createIcmp(IcmpInst.CmpType.SGE, lhs_i32, rhs_i32, "cmptmp");
                default -> i1Val = null;
            }
            lhs_i32 = builder.createZext(i1Val, IntegerType.i32, "zexttmp");
        }
        return lhs_i32;
    }

    public Value visitEqExp(EqExpNode node) {
        if (node == null) return null;
        Value lhs_i32 = visitRelExp(node.getRelExps().get(0));
        for (int i = 0; i < node.getOperators().size(); i++) {
            Token op = node.getOperators().get(i);
            Value rhs_i32 = visitRelExp(node.getRelExps().get(i + 1));
            Value i1Val;
            switch (op.getType()) {
                case EQL -> i1Val = builder.createIcmp(IcmpInst.CmpType.EQ, lhs_i32, rhs_i32, "cmptmp");
                case NEQ -> i1Val = builder.createIcmp(IcmpInst.CmpType.NE, lhs_i32, rhs_i32, "cmptmp");
                default -> i1Val = null;
            }
            lhs_i32 = builder.createZext(i1Val, IntegerType.i32, "zexttmp");
        }
        return lhs_i32;
    }

    public Value visitLAndExp(LAndExpNode node) {
        if (node == null) return null;
        if (node.getOperators().isEmpty()) {
            return visitEqExp(node.getEqExps().get(0));
        }

        Function currentFunction = hub.getCurrentFunction();
        BasicBlock trueBB = new BasicBlock(hub.getNextLandTrueLabel(), currentFunction);
        BasicBlock falseBB = new BasicBlock(hub.getNextLandFalseLabel(), currentFunction);
        BasicBlock mergeBB = new BasicBlock(hub.getNextLandMergeLabel(), currentFunction);

        // 调用控制流生成逻辑
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
        if (node == null) return null;
        if (node.getOperators().isEmpty()) {
            return visitLAndExp(node.getlAndExps().get(0));
        }

        Function currentFunction = hub.getCurrentFunction();
        BasicBlock trueBB = new BasicBlock(hub.getNextLorTrueLabel(), currentFunction);
        BasicBlock falseBB = new BasicBlock(hub.getNextLorFalseLabel(), currentFunction);
        BasicBlock mergeBB = new BasicBlock(hub.getNextLorMergeLabel(), currentFunction);

        // 调用控制流生成逻辑
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

    // ==========================================
    //          地址指针计算 (Return Pointer)
    // ==========================================

    /**
     * 获取左值表达式的内存地址 (指针)。
     * 改名自 getPointerToLVal，明确其返回的是 Pointer
     */
    public Value buildLValPointer(LValNode node) {
        Symbol symbol = hub.getCurrentScope().lookup(node.getIdent().getText());
        if (!(symbol instanceof ValueSymbol valueSymbol)) return null;

        Value basePtr = valueSymbol.getLlvmValue();

        if (node.getType() == LValNode.Type.SCALAR) {
            return basePtr;
        } else {
            Value index = visitExpression(node.getArrayExps().get(0)); // 索引计算
            Type basePtrType = basePtr.getType();
            if (basePtrType instanceof PointerType) {
                Type targetType = ((PointerType) basePtrType).getTargetType();
                if (targetType.isArrayType()) {
                    Value zero = new ConstantInt(0);
                    return builder.createGep(basePtr, List.of(zero, index), "arr.idx");
                } else if (targetType.isPointerType()) {
                    Value loadedPtr = builder.createLoad(basePtr, "arr.param.ptr");
                    return builder.createGep(loadedPtr, List.of(index), "arr.param.idx");
                }
            }
            return null;
        }
    }

    /**
     * 获取左值表达式的数值 (Load)。
     * 改名自 visitLVal_asValue，明确其行为是 Load Value
     */
    public Value buildLValValue(LValNode node) {
        Value ptr = buildLValPointer(node);
        return builder.createLoad(ptr, node.getIdent().getText() + ".val");
    }

    // ==========================================
    //          控制流生成 (Generate Branch)
    // ==========================================

    /**
     * 生成条件跳转指令。
     * 改名自 visitCond，明确其副作用是生成 Branch 而不是返回值
     */
    public void buildConditionBranch(ASTNode node, BasicBlock trueBB, BasicBlock falseBB) {
        if (node instanceof CondNode c) {
            buildConditionBranch(c.getLorExp(), trueBB, falseBB);
        } else if (node instanceof LOrExpNode lor) {
            buildOrBranch(lor, trueBB, falseBB);
        } else if (node instanceof LAndExpNode land) {
            buildAndBranch(land, trueBB, falseBB);
        } else {
            // 基本情况：计算值，然后跳转
            Value val = visitExpression(node);
            Value condVal;
            if (val.getType() == IntegerType.i1) {
                condVal = val;
            } else {
                condVal = builder.createIcmp(IcmpInst.CmpType.NE, val, new ConstantInt(0), "cond");
            }
            builder.createCondBr(condVal, trueBB, falseBB);
        }
    }

    /**
     * 逻辑或 (||) 的短路跳转实现
     */
    private void buildOrBranch(LOrExpNode node, BasicBlock trueBB, BasicBlock falseBB) {
        List<LAndExpNode> parts = node.getlAndExps();
        for (int i = 0; i < parts.size() - 1; i++) {
            BasicBlock nextBB = new BasicBlock(hub.getNextLorNextLabel(), hub.getCurrentFunction());
            buildConditionBranch(parts.get(i), trueBB, nextBB);
            builder.setInsertPoint(nextBB);
        }
        buildConditionBranch(parts.get(parts.size() - 1), trueBB, falseBB);
    }

    /**
     * 逻辑与 (&&) 的短路跳转实现
     */
    private void buildAndBranch(LAndExpNode node, BasicBlock trueBB, BasicBlock falseBB) {
        List<EqExpNode> parts = node.getEqExps();
        for (int i = 0; i < parts.size() - 1; i++) {
            BasicBlock nextBB = new BasicBlock(hub.getNextLandNextLabel(), hub.getCurrentFunction());
            buildConditionBranch(parts.get(i), nextBB, falseBB);
            builder.setInsertPoint(nextBB);
        }
        buildConditionBranch(parts.get(parts.size() - 1), trueBB, falseBB);
    }
}