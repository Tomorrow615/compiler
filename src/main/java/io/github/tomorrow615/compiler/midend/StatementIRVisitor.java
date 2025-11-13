// 完整路径: E:\compiler\src\main\java\io\github\tomorrow615\compiler\midend\StatementIRVisitor.java
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

import java.util.ArrayList;
import java.util.List;

public class StatementIRVisitor {

    private final IRGeneratorVisitor hub;
    private final IRBuilder builder;
    private final ExpressionIRVisitor exprVisitor;

    public StatementIRVisitor(IRGeneratorVisitor hub, IRBuilder builder, ExpressionIRVisitor exprVisitor) {
        this.hub = hub;
        this.builder = builder;
        this.exprVisitor = exprVisitor;
    }

    private boolean hasTerminator(BasicBlock block) {
        // 1. 从 BasicBlock 获取指令列表 [cite: 2130-2142]
        List<Instruction> insts = block.getInstructions();
        if (insts.isEmpty()) {
            return false;
        }
        // 2. 获取列表中的最后一条指令
        Instruction lastInst = insts.get(insts.size() - 1);

        // 3. 检查它是否是你的项目 [cite: 488-1746] 中定义的终结符
        return (lastInst instanceof BranchInst || lastInst instanceof ReturnInst);
    }

    public void visit(StmtNode node) {
        if (node == null) return;

        if (node instanceof AssignStmtNode a) {
            visit(a);
        } else if (node instanceof ExpStmtNode e) {
            visit(e);
        } else if (node instanceof ReturnStmtNode r) {
            visit(r);
        } else if (node instanceof PrintfStmtNode p) {
            visit(p);
        } else if (node instanceof IfStmtNode i) {
            visit(i);
        } else if (node instanceof ForStmtNode f) {
            visit(f);
        } else if (node instanceof BreakStmtNode b) {
            visit(b);
        } else if (node instanceof ContinueStmtNode c) {
            visit(c);

            // --- [ START 修复 5.1-ForLoop ] ---
            // 当 Stmt 是一个 BlockNode 时 (例如 for/if 后的 { ... })
            // 我们必须委托 Hub (IRGeneratorVisitor) 来处理，
            // 因为 Hub 负责 enterScope 和 exitScope。
        } else if (node instanceof BlockNode b) {
            hub.enterScope();
            hub.visit(b);     // [关键] 调用 Hub 的 visit(BlockNode) ，它会遍历 BlockItem
            hub.exitScope();
        }
        // --- [ END 修复 5.1-ForLoop ] ---
    }

    private void visit(AssignStmtNode node) {
        // 1. 计算右侧 Exp 的值
        Value valToStore = exprVisitor.visit(node.getExp());

        // 2. 获取左侧 LVal 的指针
        Value ptr = exprVisitor.getPointerToLVal(node.getlVal());

        // 3. 创建 store 指令
        if (valToStore != null && ptr != null) {
            builder.createStore(valToStore, ptr);
        }
    }

    private void visit(ExpStmtNode node) {
        if (node.getExp() != null) {
            // 只 visit，不使用返回值 (用于执行函数调用并丢弃结果)
            exprVisitor.visit(node.getExp());
        }
    }

    private void visit(ReturnStmtNode node) {
        if (node.getExp() != null) {
            // 有返回值 'return <exp>;'
            Value retVal = exprVisitor.visit(node.getExp());
            builder.createRet(retVal);
        } else {
            // 'return;' (void)
            builder.createRetVoid();
        }
    }


    private void visit(PrintfStmtNode node) {
        Token formatStringToken = node.getFormatString();
        String formatString = (String) formatStringToken.getValue();
        List<ExpNode> exps = node.getExps();

        int expIndex = 0;
        Function putintFunc = hub.getIoFunctions().get("putint");
        Function putstrFunc = hub.getIoFunctions().get("putstr");

        // 用于收集连续的普通字符串
        StringBuilder strFragment = new StringBuilder();

        for (int i = 0; i < formatString.length(); i++) {
            if (formatString.charAt(i) == '%') {
                if (i + 1 < formatString.length() && formatString.charAt(i + 1) == 'd') {
                    // 1. 遇到 %d，先把前面收集的字符串打印出去
                    if (strFragment.length() > 0) {
                        printStringFragment(strFragment.toString(), putstrFunc);
                        strFragment.setLength(0); // 清空
                    }

                    // 2. 打印 %d 对应的表达式
                    if (expIndex < exps.size()) {
                        Value val = exprVisitor.visit(exps.get(expIndex++));
                        builder.createCall(putintFunc, List.of(val), "");
                    }
                    i++; // 跳过 'd'
                } else {
                    // 无法识别的 % (例如 %% 或 %a)，当作普通字符
                    strFragment.append(formatString.charAt(i));
                }
            } else {
                // 普通字符，加入片段
                strFragment.append(formatString.charAt(i));
            }
        }

        // 打印循环结束后剩余的最后一段字符串
        if (strFragment.length() > 0) {
            printStringFragment(strFragment.toString(), putstrFunc);
        }
    }

    private void printStringFragment(String text, Function putstrFunc) {
        // 1. 从 Hub 获取或创建全局字符串常量
        GlobalVariable gv = hub.getGlobalString(text);

        // 2. 创建 GEP 指令获取 i8*
        // GEP @.str, i32 0, i32 0
        Value zero = new ConstantInt(0);
        Value ptr = builder.createGep(gv, List.of(zero, zero), "str.ptr");

        // 3. 调用 putstr
        builder.createCall(putstrFunc, List.of(ptr), "");
    }

    private void visit(IfStmtNode node) {
        // 1. 从 Hub 获取 Function，用于创建 BasicBlock
        Function currentFunction = hub.getCurrentFunction(); // [cite: 830, 1122]

        // 2. 创建所需的基本块
        BasicBlock thenBB = new BasicBlock(hub.getNextIfThenLabel(), currentFunction);
        BasicBlock mergeBB = new BasicBlock(hub.getNextIfMergeLabel(), currentFunction);
        BasicBlock elseBB = null;

        // 3. 检查是否存在 else 分支
        boolean hasElse = (node.getElseStmt() != null);
        if (hasElse) {
            elseBB = new BasicBlock(hub.getNextIfElseLabel(), currentFunction);
        }

        // 4. 计算条件表达式 (返回 i32)
        // [cite: 66, 734]
        Value cond_i32 = exprVisitor.visit(node.getCond());

        // 5. 将 i32 条件转为 i1 (icmp ne i32 %cond, 0)
        // [cite: 67, 812]
        Value cond_i1 = builder.createIcmp(
                IcmpInst.CmpType.NE, // [cite: 67, 779]
                cond_i32,
                new ConstantInt(0), // [cite: 67, 1138]
                "if.cond"
        );

        // 6. 创建条件跳转指令
        if (hasElse) {
            // If-Then-Else: br i1 %if.cond, label %if.then, label %if.else
            builder.createCondBr(cond_i1, thenBB, elseBB); // [cite: 70, 816]
        } else {
            // If-Then: br i1 %if.cond, label %if.then, label %if.merge
            builder.createCondBr(cond_i1, thenBB, mergeBB); // [cite: 70, 816]
        }

        // 7. 填充 then 块
        builder.setInsertPoint(thenBB); // [cite: 71, 799]
        visit(node.getThenStmt()); // 递归访问 then 语句体 [cite: 683, 902]
        if (!hasTerminator(builder.getCurrentBlock())) {
            builder.createBr(mergeBB);
        }

        // 8. 填充 else 块 (如果存在)
        if (hasElse) {
            builder.setInsertPoint(elseBB);
            visit(node.getElseStmt()); // 递归访问 else 语句体 [cite: 683, 902]
            if (!hasTerminator(builder.getCurrentBlock())) {
                builder.createBr(mergeBB);
            }
        }

        // 9. 将插入点移动到 merge 块，后续代码将在这里生成
        builder.setInsertPoint(mergeBB); // [cite: 69, 799]
    }

    private void visit(ForStmtNode node) {
        Function currentFunction = hub.getCurrentFunction();

        // 1. 创建所需的基本块
        BasicBlock condBB = new BasicBlock("for.cond", currentFunction);
        BasicBlock bodyBB = new BasicBlock("for.body", currentFunction);
        BasicBlock updateBB = new BasicBlock("for.update", currentFunction);
        BasicBlock mergeBB = new BasicBlock("for.merge", currentFunction);

        // 2. [Hub] 注册 break 和 continue 的跳转目标
        hub.pushLoop(mergeBB, updateBB);

        // 3. [Init] 访问 ForStmt1 (初始化)
        if (node.getInitStmt() != null) {
            // ForSubStmtNode 包含 LVal=Exp 列表
            ForSubStmtNode init = node.getInitStmt();
            // 对应文法: ForStmt → LVal '=' Exp { ',' Lval '=' Exp}
            for (int i = 0; i < init.getLVals().size(); i++) {
                Value val = exprVisitor.visit(init.getExps().get(i));
                Value ptr = exprVisitor.getPointerToLVal(init.getLVals().get(i));
                builder.createStore(val, ptr);
            }
        }
        // 初始化后，无条件跳转到 condBB
        builder.createBr(condBB);

        // 4. [Cond] 填充 condBB
        builder.setInsertPoint(condBB);
        if (node.getCond() != null) {
            // 有条件
            Value cond_i32 = exprVisitor.visit(node.getCond());
            Value cond_i1 = builder.createIcmp(IcmpInst.CmpType.NE, cond_i32, new ConstantInt(0), "for.cond.bool");
            // br i1 %cond, label %body, label %merge
            builder.createCondBr(cond_i1, bodyBB, mergeBB);
        } else {
            // 无条件 (for(;;))，总是进入 body
            builder.createBr(bodyBB);
        }

        // 5. [Body] 填充 bodyBB
        builder.setInsertPoint(bodyBB);
        visit(node.getBodyStmt()); // 递归访问循环体

        // 1. 从 BasicBlock 获取指令列表
        List<Instruction> insts = builder.getCurrentBlock().getInstructions();
        boolean hasTerminator = false;

        if (!insts.isEmpty()) {
            // 2. 获取列表中的最后一条指令
            Instruction lastInst = insts.get(insts.size() - 1);

            // 3. 检查它是否是你的项目  中定义的终结符
            if (lastInst instanceof BranchInst || lastInst instanceof ReturnInst) {
                hasTerminator = true;
            }
        }

        // 4. 仅在没有终结符时才添加 'br'
        if (!hasTerminator) {
            builder.createBr(updateBB); // 循环体末尾无条件跳转到 updateBB
        }

        // 6. [Update] 填充 updateBB
        builder.setInsertPoint(updateBB);
        if (node.getUpdateStmt() != null) {
            // 访问 ForStmt2 (更新)
            ForSubStmtNode update = node.getUpdateStmt();
            for (int i = 0; i < update.getLVals().size(); i++) {
                Value val = exprVisitor.visit(update.getExps().get(i));
                Value ptr = exprVisitor.getPointerToLVal(update.getLVals().get(i));
                builder.createStore(val, ptr);
            }
        }
        builder.createBr(condBB); // 更新后无条件跳转回 condBB

        // 7. [Merge] 将插入点移动到 mergeBB
        builder.setInsertPoint(mergeBB);

        // 8. [Hub] 退出循环，恢复 break/continue 栈
        hub.popLoop();
    }

    private void visit(BreakStmtNode node) {
        // SemanticVisitor 已经检查过 loopDepth
        // 我们只需要跳转到 Hub 告知的 mergeBB
        builder.createBr(hub.getCurrentLoopMergeBB());
    }

    private void visit(ContinueStmtNode node) {
        // SemanticVisitor 已经检查过 loopDepth
        // 我们只需要跳转到 Hub 告知的 updateBB
        builder.createBr(hub.getCurrentLoopUpdateBB());
    }
}