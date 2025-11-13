package io.github.tomorrow615.compiler.midend;

// 导入 AST 节点
import io.github.tomorrow615.compiler.frontend.ast.CompUnitNode;
import io.github.tomorrow615.compiler.frontend.ast.func.MainFuncDefNode;
// 导入我们创建的 midend 类
import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.instruction.ReturnInst;
import io.github.tomorrow615.compiler.midend.llvm.type.FunctionType;
import io.github.tomorrow615.compiler.midend.llvm.type.IntegerType;
import io.github.tomorrow615.compiler.midend.llvm.value.BasicBlock;
import io.github.tomorrow615.compiler.midend.llvm.value.ConstantInt;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;

import java.util.ArrayList;

/**
 * 最小实现，仅用于 "Hello World" 测试 (阶段 3.4)
 */
public class IRGeneratorVisitor {

    private final CompUnitNode astRoot;
    private final Module module;

    public IRGeneratorVisitor(CompUnitNode astRoot) {
        this.astRoot = astRoot;
        this.module = new Module();
    }

    /**
     * 启动 IR 生成
     */
    public Module generate() {
        // 暂时只访问 main 函数
        visit(astRoot.getMainFuncDef());
        return this.module;
    }

    /**
     * 手动为 "int main() { return 0; }" 构建 IR
     */
    private void visit(MainFuncDefNode node) {
        // 1. 创建函数类型: i32 ()
        FunctionType mainType = new FunctionType(IntegerType.i32, new ArrayList<>());

        // 2. 创建函数: @main
        Function mainFunc = new Function(mainType, "@main");
        this.module.addFunction(mainFunc);

        // 3. 创建入口块: "entry"
        // 构造函数会自动将 bb 添加到 mainFunc [cite: 331, 333]
        BasicBlock entryBB = new BasicBlock("entry", mainFunc);

        // 4. 创建常量: 0
        ConstantInt zero = new ConstantInt(0);

        // 5. 创建指令: ret i32 0
        // 构造函数会自动将指令添加到 entryBB [cite: 262, 247]
        new ReturnInst(zero, entryBB);
    }
}
