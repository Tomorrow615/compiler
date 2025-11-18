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
        // 1. 查找符号 (来自 Pass 1)
        Symbol symbol = hub.getCurrentScope().lookup(node.getIdent().getText());
        if (symbol == null) {
            return null;
        }
        if (!(symbol instanceof ValueSymbol valueSymbol)) {
            return null;
        }

        // 2. 获取 basePtr。
        //    - 局部/全局变量: basePtr 是 [N x i32]*
        //    - 数组参数:     basePtr 是 i32** (指向 i32* 的指针)
        Value basePtr = valueSymbol.getLlvmValue();

        if (node.getType() == LValNode.Type.SCALAR) { //
            // 访问 'a' (标量)
            return basePtr; // 返回 i32*
        } else {
            // 访问 'a[i]' (数组元素)
            // 1. 获取索引值 (i32)
            Value index = visit(node.getArrayExps().get(0)); // 递归 visit(ExpNode)

            // 2. 检查 basePtr 的类型 (i32** 还是 [N x i32]*)
            Type basePtrType = basePtr.getType();
            if (basePtrType instanceof PointerType) { // [cite: 2207-2211]
                Type targetType = ((PointerType) basePtrType).getTargetType(); // [cite: 2209]

                if (targetType.isArrayType()) { // [cite: 2188-2193]
                    // 情况 A: basePtr 是 [N x i32]* (局部/全局数组)
                    // 我们需要 GEP (ptr, 0, index)
                    Value zero = new ConstantInt(0);
                    // [cite: 1911]
                    return builder.createGep(basePtr, List.of(zero, index), "arr.idx");
                } else if (targetType.isPointerType()) { // [cite: 2207-2211]
                    // 情况 B: basePtr 是 i32** (数组参数 alloca 后的地址)
                    // 1. 先 load 拿到 i32* (即函数参数 %0)
                    // [cite: 1909]
                    Value loadedPtr = builder.createLoad(basePtr, "arr.param.ptr");
                    // 2. GEP (loadedPtr, index) -> i32*
                    // [cite: 1911]
                    return builder.createGep(loadedPtr, List.of(index), "arr.param.idx");
                }
            }
            // 理论上不应到达这里
            return null;
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
                if (!(symbol instanceof FuncSymbol funcSymbol)) {
                    return null;
                }

                // 2. 获取 Function 对象
                Function callee = (Function) funcSymbol.getLlvmValue();

                // 3. 准备参数
                List<Value> args = new ArrayList<>();
                // [修改] 获取期望的形参列表
                List<ValueSymbol> expectedParams = funcSymbol.getParameters(); // [cite: 1602]

                if (node.getFuncRParams() != null) {
                    List<ExpNode> actualParams = node.getFuncRParams().getParams();

                    for (int i = 0; i < actualParams.size(); i++) {
                        ExpNode argExp = actualParams.get(i);
                        // [修改] 获取对应的形参
                        ValueSymbol expectedParam = expectedParams.get(i);

                        if (expectedParam.getDimension() > 0) {
                            // 期望一个数组 (i32*)

                            // 1. 找到 LVal 节点
                            LValNode lvalNode = null;
                            try {
                                // 尝试解析 Exp -> ... -> LVal [cite: 1185-1187, 1171-1174, 1205-1208, 1228-1242, 1212-1223, 1199-1204]
                                lvalNode = ((PrimaryExpNode) ((UnaryExpNode) ((MulExpNode) argExp.getAddExp().getMulExps().get(0)).getUnaryExps().get(0)).getPrimaryExp()).getLval();
                            } catch (Exception e) { /* 忽略转换失败 */ }

                            if (lvalNode != null && lvalNode.getType() == LValNode.Type.SCALAR) {
                                // 传递的是 'a'，而不是 'a[i]'

                                // 2. 获取 [N x i32]* (或 i32**)
                                Value basePtr = getPointerToLVal(lvalNode); //

                                Type basePtrType = basePtr.getType();
                                if (basePtrType instanceof PointerType) {
                                    Type targetType = ((PointerType) basePtrType).getTargetType();
                                    if (targetType.isArrayType()) {
                                        // 3a. "衰变" (decay) [N x i32]* 为 i32*
                                        Value zero = new ConstantInt(0);
                                        Value decayedPtr = builder.createGep(basePtr, List.of(zero, zero), "arr.decay");
                                        args.add(decayedPtr);
                                    } else if (targetType.isPointerType()) {
                                        // 3b. basePtr 是 i32** (数组参数)，load 它得到 i32*
                                        Value loadedPtr = builder.createLoad(basePtr, "arr.param.decay");
                                        args.add(loadedPtr);
                                    }
                                }
                            } else {
                                // 传递的是 a[i] (i32) 或其他表达式 (i32)
                                // 但期望 i32*
                                // SemanticVisitor 应该已经报错 (e) [cite: 1692-1693]
                                args.add(visit(argExp)); // 仍然添加 i32，让 LLVM 报错
                            }

                        } else {
                            // 期望标量 (i32)
                            args.add(visit(argExp)); // 保持原逻辑
                        }
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
        if (node == null) return null;

        // 1. 递归访问第一个 EqExp，获取 i32 值
        Value lhs_i32 = visit(node.getEqExps().get(0));

        // 如果没有 '&&' 操作符，直接返回结果
        if (node.getOperators().isEmpty()) {
            return lhs_i32;
        }

        // 2. 递归访问第二个 EqExp，获取 i32 值
        // (注意：这只支持 a && b，不支持 a && b && c，但这和你的原代码限制一致)
        Value rhs_i32 = visit(node.getEqExps().get(1));

        // 3. 将两个 i32 转换为 i1 (bool)
        Value lhs_i1 = builder.createIcmp(IcmpInst.CmpType.NE, lhs_i32, new ConstantInt(0), "land.lhs.bool");
        Value rhs_i1 = builder.createIcmp(IcmpInst.CmpType.NE, rhs_i32, new ConstantInt(0), "land.rhs.bool");

        // 4. 对两个 i1 执行 'and' 运算
        Value result_i1 = builder.createAnd(lhs_i1, rhs_i1, "land.res");

        // 5. 将 i1 结果零扩展回 i32，供 StatementIRVisitor 使用
        return builder.createZext(result_i1, IntegerType.i32, "zexttmp");
    }

    public Value visit(LOrExpNode node) {
        if (node == null) return null;

        // 1. 递归访问第一个 LAndExp，获取 i32 值
        Value lhs_i32 = visit(node.getlAndExps().get(0));

        // 如果没有 '||' 操作符，直接返回结果
        if (node.getOperators().isEmpty()) {
            return lhs_i32;
        }

        // 2. 递归访问第二个 LAndExp，获取 i32 值
        Value rhs_i32 = visit(node.getlAndExps().get(1));

        // 3. 将两个 i32 转换为 i1 (bool)
        Value lhs_i1 = builder.createIcmp(IcmpInst.CmpType.NE, lhs_i32, new ConstantInt(0), "lor.lhs.bool");
        Value rhs_i1 = builder.createIcmp(IcmpInst.CmpType.NE, rhs_i32, new ConstantInt(0), "lor.rhs.bool");

        // 4. 对两个 i1 执行 'or' 运算
        Value result_i1 = builder.createOr(lhs_i1, rhs_i1, "lor.res");

        // 5. 将 i1 结果零扩展回 i32
        return builder.createZext(result_i1, IntegerType.i32, "zexttmp");
    }

    /*
    public Value visit(LAndExpNode node) {
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

     */
}