// 文件路径: src/.../frontend/visitor/ASTPrinter.java
package io.github.tomorrow615.compiler.frontend.visitor;

import io.github.tomorrow615.compiler.frontend.ast.*;
import io.github.tomorrow615.compiler.frontend.ast.decl.*;
import io.github.tomorrow615.compiler.frontend.ast.expr.*;
import io.github.tomorrow615.compiler.frontend.ast.func.*;
import io.github.tomorrow615.compiler.frontend.lexer.Token;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.List;

public class ASTPrinter {
    private final PrintWriter writer;

    public ASTPrinter(PrintWriter writer) {
        this.writer = writer;
    }

    public void print(ASTNode node) {
        traverse(node);
    }

    // 使用反射来通用地遍历所有子节点
    private void traverse(Object obj) {
        if (obj == null) return;

        if (obj instanceof ASTNode) {
            // 这是一个AST节点
            try {
                // 获取所有字段
                for (Field field : obj.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    Object child = field.get(obj);
                    traverse(child); // 递归遍历子字段
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            // 后序遍历：在所有子节点都处理完毕后，打印当前节点名
            if (!(obj.getClass().getSimpleName().equals("BTypeNode") ||
                    obj.getClass().getSimpleName().equals("DeclNode"))) {
                // 根据题目要求，<BlockItem>, <Decl>, <BType> 之外的才输出
                writer.println("<" + obj.getClass().getSimpleName().replace("Node", "") + ">");
            }
        } else if (obj instanceof List) {
            // 这是一个列表，遍历列表中的每个元素
            for (Object item : (List<?>) obj) {
                traverse(item);
            }
        } else if (obj instanceof Token) {
            // 这是一个Token，直接打印
            writer.println(((Token) obj).getType() + " " + ((Token) obj).getText());
        }
    }
}