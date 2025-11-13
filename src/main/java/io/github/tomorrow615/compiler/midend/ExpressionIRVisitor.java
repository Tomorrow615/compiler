package io.github.tomorrow615.compiler.midend;

import io.github.tomorrow615.compiler.frontend.ast.*;
import io.github.tomorrow615.compiler.frontend.ast.decl.*;
import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.ast.func.*;
import io.github.tomorrow615.compiler.frontend.ast.stmt.*;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import io.github.tomorrow615.compiler.frontend.lexer.TokenType;
import io.github.tomorrow615.compiler.frontend.symbol.*;
import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.type.*;
import io.github.tomorrow615.compiler.midend.llvm.value.*;

import java.util.List;
import java.util.ArrayList;

public class ExpressionIRVisitor {

    private final IRGeneratorVisitor hub; // 对主 Visitor (Hub) 的引用
    private final IRBuilder builder; // 快捷方式

    public ExpressionIRVisitor(IRGeneratorVisitor hub, IRBuilder builder) {
        this.hub = hub;
        this.builder = builder;
    }

    public Value visit(InitValNode node) {
        if (node == null) return null;
        if (node.getType() == InitValNode.Type.SINGLE) {
            return visit(node.getSingleInit());
        }
        return null; // 暂不支持数组
    }

    public Value visit(ConstInitValNode node) {
        if (node == null) return null;
        if (node.getType() == ConstInitValNode.Type.SINGLE) {
            return visit(node.getSingleInit());
        }
        return null; // 暂不支持数组
    }

    public Value visit(ConstExpNode node) {
        if(node == null) return null;
        return visit(node.getAddExp());
    }

    public Value visit(ExpNode node) {
        if(node == null) return null;
        return visit(node.getAddExp());
    }

    public Value visit(CondNode node) {
        if(node == null) return null;
        // Cond 必须返回 i32 类型的 0 或 1
        return visit(node.getLorExp());
    }


    public Value getPointerToLVal(LValNode node) {
        // 1. 查找符号
        // [修改] 通过 hub 获取 currentScope
        Symbol symbol = hub.getCurrentScope().lookup(node.getIdent().getText());
        if (symbol == null) {
            return null;
        }

        // [ 错误修复 ]
        if (!(symbol instanceof ValueSymbol valueSymbol)) {
            return null;
        }
        Value basePtr = valueSymbol.getLlvmValue();
        // [ 修复结束 ]

        if (node.getType() == LValNode.Type.SCALAR) {
            return basePtr;
        } else {
            // TODO: (阶段 5) 数组 GEP 逻辑
            return basePtr;
        }
    }

    /**
     * (辅助方法) 访问 LVal 以获取其 *值*
     */
    public Value visitLVal_asValue(LValNode node) {
        Value ptr = getPointerToLVal(node);
        return builder.createLoad(ptr, node.getIdent().getText() + ".val");
    }


    public Value visit(PrimaryExpNode node) {
        if (node == null) return null;
        return switch (node.getType()) {
            case LVAL ->
                    visitLVal_asValue(node.getLval());
            case NUMBER ->
                    new ConstantInt(Integer.parseInt(node.getNumber().getIntConst().getText()));
            case PAREN_EXP ->
                    visit(node.getExp());
        };
    }

    public Value visit(UnaryExpNode node) {
        if (node == null) return null;

        switch (node.getType()) {
            case PRIMARY:
                return visit(node.getPrimaryExp());
            case UNARY_OP:
                Value val = visit(node.getUnaryExp());
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
                // 1. 查找函数符号
                Token ident = node.getIdent();
                Symbol symbol = hub.getCurrentScope().lookup(ident.getText());

                // (健壮性检查，SemanticVisitor 应该已处理)
                if (!(symbol instanceof FuncSymbol funcSymbol)) {
                    return null;
                }

                // 2. 获取 Function 对象 (来自全局声明或 4.4 的 visitFuncDef)
                Function callee = (Function) funcSymbol.getLlvmValue();

                // 3. 准备参数
                List<Value> args = new ArrayList<>();
                if (node.getFuncRParams() != null) {
                    for (ExpNode argExp : node.getFuncRParams().getParams()) {
                        args.add(visit(argExp)); // 递归调用 visit(ExpNode)
                    }
                }

                // 4. 创建 Call 指令
                String callName = (callee.getReturnType().isVoidType()) ? "" : ident.getText() + ".call";
                return builder.createCall(callee, args, callName);
            // --- [ 4.6 修改结束 ] ---
        }
        return null; // 暂未实现
    }

    public Value visit(MulExpNode node) {
        // ... [ 粘贴 4.5 的 MulExpNode 逻辑 ] ...
        if (node == null) return null;
        Value lhs = visit(node.getUnaryExps().get(0));
        for (int i = 0; i < node.getOperators().size(); i++) {
            Token op = node.getOperators().get(i);
            Value rhs = visit(node.getUnaryExps().get(i + 1));
            switch (op.getType()) {
                case MULT -> lhs = builder.createMul(lhs, rhs, "multmp");
                case DIV -> lhs = builder.createSdiv(lhs, rhs, "divtmp");
                case MOD -> lhs = builder.createSrem(lhs, rhs, "modtmp");
            }
        }
        return lhs;
    }

    public Value visit(AddExpNode node) {
        // ... [ 粘贴 4.5 的 AddExpNode 逻辑 ] ...
        if (node == null) return null;
        Value lhs = visit(node.getMulExps().get(0));
        for (int i = 0; i < node.getOperators().size(); i++) {
            Token op = node.getOperators().get(i);
            Value rhs = visit(node.getMulExps().get(i + 1));
            switch (op.getType()) {
                case PLUS -> lhs = builder.createAdd(lhs, rhs, "addtmp");
                case MINU -> lhs = builder.createSub(lhs, rhs, "subtmp");
            }
        }
        return lhs;
    }

    public Value visit(RelExpNode node) {
        // ... [ 粘贴 4.5 的 RelExpNode 逻辑 ] ...
        if (node == null) return null;
        Value lhs_i32 = visit(node.getAddExps().get(0));
        for (int i = 0; i < node.getOperators().size(); i++) {
            Token op = node.getOperators().get(i);
            Value rhs_i32 = visit(node.getAddExps().get(i + 1));
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

    public Value visit(EqExpNode node) {
        // ... [ 粘贴 4.5 的 EqExpNode 逻辑 ] ...
        if (node == null) return null;
        Value lhs_i32 = visit(node.getRelExps().get(0));
        for (int i = 0; i < node.getOperators().size(); i++) {
            Token op = node.getOperators().get(i);
            Value rhs_i32 = visit(node.getRelExps().get(i + 1));
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

    public Value visit(LAndExpNode node) {
        // ... [ 粘贴 4.5 的 LAndExpNode 逻辑 ] ...
        if (node == null) return null;
        Value lhs_i32 = visit(node.getEqExps().get(0));
        if (node.getOperators().isEmpty()) {
            return lhs_i32;
        }
        Value lhs_i1 = builder.createIcmp(IcmpInst.CmpType.NE, lhs_i32, new ConstantInt(0), "land.lhs.bool");
        BasicBlock lhsEndBB = builder.getCurrentBlock();
        // [修改] 通过 hub 获取 currentFunction
        Function currentFunction = hub.getCurrentFunction();
        BasicBlock rhsBB = new BasicBlock("land.rhs", currentFunction);
        BasicBlock mergeBB = new BasicBlock("land.merge", currentFunction);
        builder.createCondBr(lhs_i1, rhsBB, mergeBB);
        builder.setInsertPoint(rhsBB);
        Value rhs_i32 = visit(node.getEqExps().get(1));
        Value rhs_i1 = builder.createIcmp(IcmpInst.CmpType.NE, rhs_i32, new ConstantInt(0), "land.rhs.bool");
        BasicBlock rhsEndBB = builder.getCurrentBlock();
        builder.createBr(mergeBB);
        builder.setInsertPoint(mergeBB);
        PhiInst phi = builder.createPhi(IntegerType.i1, "land.phi");
        phi.addIncoming(new ConstantInt(false), lhsEndBB);
        phi.addIncoming(rhs_i1, rhsEndBB);
        return builder.createZext(phi, IntegerType.i32, "zexttmp");
    }

    public Value visit(LOrExpNode node) {
        // ... [ 粘贴 4.5 的 LOrExpNode 逻辑 ] ...
        if (node == null) return null;
        Value lhs_i32 = visit(node.getlAndExps().get(0));
        if (node.getOperators().isEmpty()) {
            return lhs_i32;
        }
        Value lhs_i1 = builder.createIcmp(IcmpInst.CmpType.NE, lhs_i32, new ConstantInt(0), "lor.lhs.bool");
        BasicBlock lhsEndBB = builder.getCurrentBlock();
        // [修改] 通过 hub 获取 currentFunction
        Function currentFunction = hub.getCurrentFunction();
        BasicBlock rhsBB = new BasicBlock("lor.rhs", currentFunction);
        BasicBlock mergeBB = new BasicBlock("lor.merge", currentFunction);
        builder.createCondBr(lhs_i1, mergeBB, rhsBB);
        builder.setInsertPoint(rhsBB);
        Value rhs_i32 = visit(node.getlAndExps().get(1));
        Value rhs_i1 = builder.createIcmp(IcmpInst.CmpType.NE, rhs_i32, new ConstantInt(0), "lor.rhs.bool");
        BasicBlock rhsEndBB = builder.getCurrentBlock();
        builder.createBr(mergeBB);
        builder.setInsertPoint(mergeBB);
        PhiInst phi = builder.createPhi(IntegerType.i1, "lor.phi");
        phi.addIncoming(new ConstantInt(true), lhsEndBB);
        phi.addIncoming(rhs_i1, rhsEndBB);
        return builder.createZext(phi, IntegerType.i32, "zexttmp");
    }
}