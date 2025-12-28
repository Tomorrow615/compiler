package io.github.tomorrow615.compiler.backend.codegen;

import io.github.tomorrow615.compiler.backend.regalloc.GraphColoringAllocator;
import io.github.tomorrow615.compiler.backend.mips.MipsModule;
import io.github.tomorrow615.compiler.backend.mips.MipsRegister;
import io.github.tomorrow615.compiler.backend.mips.assembly.*;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsBranch;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsInstruction;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsLoadStore;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsBasicBlock;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;
import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.type.ArrayType;
import io.github.tomorrow615.compiler.midend.llvm.type.IntegerType;
import io.github.tomorrow615.compiler.midend.llvm.type.PointerType;
import io.github.tomorrow615.compiler.midend.llvm.value.*;
import io.github.tomorrow615.compiler.midend.llvm.value.Constant;

import java.util.ArrayList;
import java.util.List;

/**
 * MIPS 代码生成器核心
 * 负责调度整个翻译过程：全局变量 -> 函数 -> 基本块 -> 指令
 */
public class MipsGenerator {
    private final Module llvmModule;
    private final MipsModule mipsModule;

    public MipsGenerator(Module llvmModule) {
        this.llvmModule = llvmModule;
        this.mipsModule = new MipsModule();
    }

    public MipsModule generate() {
        // 1. 生成全局数据段 (.data)
        generateGlobalData();

        // 2. 生成代码段 (.text)
        generateFunctions();

        // 3. 生成 SysY 运行库函数 (getint, putint 等)
        generateSysYLibrary();
        
        // [新增] 4. 执行寄存器分配 (NaiveAllocator for Phase 1)
        new GraphColoringAllocator(mipsModule).allocate();
        // new io.github.tomorrow615.compiler.backend.regalloc.NaiveAllocator(mipsModule).allocate();

        return mipsModule;
    }

    private void generateSysYLibrary() {
        // getint: 读入整数 (syscall 5)
        // int getint()
        createSyscallFunc("getint", 5);

        // getch: 读入字符 (syscall 12)
        // int getch()
        createSyscallFunc("getch", 12);

        // putint: 输出整数 (syscall 1)
        // void putint(int a) - 参数已在 $a0
        createSyscallFunc("putint", 1);

        // putch: 输出字符 (syscall 11)
        // void putch(int a) - 参数已在 $a0
        createSyscallFunc("putch", 11);

        // putstr: 输出字符串 (syscall 4)
        // void putstr(char *s) - 参数已在 $a0
        createSyscallFunc("putstr", 4);
    }

    private void createSyscallFunc(String name, int syscallCode) {
        MipsFunction func = new MipsFunction(name);
        // 创建函数入口基本块，标签即为函数名 (例如 "getint:")
        MipsBasicBlock block = new MipsBasicBlock(name);

        // li $v0, code
        block.addInstruction(new MipsLi(MipsRegister.V0, syscallCode));
        // syscall
        block.addInstruction(new MipsSyscall());
        // jr $ra (返回)
        block.addInstruction(new MipsBranch("jr", MipsRegister.RA));

        func.addBasicBlock(block);
        mipsModule.addFunction(func);
    }

    /**
     * 翻译全局变量
     * SysY 中只有 int 变量、int 数组、字符串常量
     */
    private void generateGlobalData() {
        for (GlobalVariable gv : llvmModule.getGlobalVariables()) {
            // 获取变量名 (去掉 @ 前缀)
            String label = gv.getName().substring(1);
            StringBuilder sb = new StringBuilder();

            sb.append(label).append(": ");

            Constant initVal = gv.getInitializer();
            // 这里的类型判断需要根据你的 Constant 类层次结构调整
            if (initVal instanceof ConstantInt constantInt) {
                // 普通 int: .word 10
                sb.append(".word ").append(constantInt.getValue());
            } else if (initVal instanceof ConstantArray constantArray) {
                // 数组: .word 1, 2, 3...
                // 如果是 zeroinitializer (ConstantArray 内部 elements 为空或全0逻辑)，可以用 .space
                if (constantArray.getElements().isEmpty()) { // 假设空代表 zeroinitializer
                    // 计算大小: 元素个数 * 4
                    int size = ((ArrayType)((PointerType)gv.getType()).getTargetType()).getNumElements() * 4;
                    sb.append(".space ").append(size);
                } else {
                    sb.append(".word ");
                    List<Constant> elements = constantArray.getElements();
                    for (int i = 0; i < elements.size(); i++) {
                        if (elements.get(i) instanceof ConstantInt val) {
                            sb.append(val.getValue());
                        } else {
                            sb.append("0");
                        }
                        if (i < elements.size() - 1) sb.append(", ");
                    }
                }
            } else if (initVal instanceof ConstantString constantString) {
                // [修复开始]
                // 1. 获取原始字符串内容
                String rawContent = constantString.getContent();

                // 2. 转义处理：将换行符等转换为汇编字符串格式
                // MIPS 汇编中，字符串里的换行需要写成 \n
                String asmContent = rawContent.replace("\n", "\\n").replace("\"", "\\\"");

                // 3. 拼接 .asciiz 指令
                sb.append(".asciiz \"").append(asmContent).append("\"");
                // [修复结束]
            }

            mipsModule.addGlobalData(sb.toString());
        }
    }

    private void generateFunctions() {
        // 先生成 main 函数
        for (Function func : llvmModule.getFunctions()) {
            if (func.getName().equals("@main")) {
                generateFunction(func);
                break;
            }
        }

        // 再生成其他函数
        for (Function func : llvmModule.getFunctions()) {
            if (func.isDeclaration()) continue;
            if (func.getName().equals("@main")) continue; // 跳过已生成的 main

            generateFunction(func);
        }
    }

    private void generateFunction(Function func) {
        // 1. 创建 MipsFunction
        String funcName = func.getName().substring(1); // 去掉 @
        MipsFunction mipsFunc = new MipsFunction(funcName);
        mipsModule.addFunction(mipsFunc);

        // 2. 栈帧分析 (Pre-Scan)
        StackManager stackManager = new StackManager(func);

        // 3. 构建函数序言 (Prologue) -> 放入入口基本块
        // 我们创建一个名为 "funcName_entry" 的 MipsBasicBlock
        MipsBasicBlock entryBlock = new MipsBasicBlock(funcName);
        mipsFunc.addBasicBlock(entryBlock);

        // 3.1 开栈: subu $sp, $sp, frameSize
        int frameSize = stackManager.getFrameSize();
        if (frameSize > 0) {
            entryBlock.addInstruction(new MipsBinary("subu", MipsRegister.SP, MipsRegister.SP, frameSize));
        }

        // 3.2 保存 $ra: sw $ra, raOffset($sp)
        int raOffset = stackManager.getRaOffset();
        entryBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.SW, MipsRegister.RA, MipsRegister.SP, raOffset));

        // 3.3 保存参数 (Arguments) 到栈上
        // 策略：为了统一后续处理，我们将寄存器传来的参数 ($a0-$a3) 立即存入栈中 StackManager 分配的位置
        // 这样后续使用参数时，统一用 lw 从栈里取，逻辑简单
        List<Argument> args = func.getArguments();
        for (int i = 0; i < args.size(); i++) {
            Argument arg = args.get(i);
            int offset = stackManager.getOffset(arg);
            if (i < 4) {
                // 前4个参数在寄存器 $a0-$a3
                MipsRegister argReg = MipsRegister.values()[MipsRegister.A0.getId() + i];
                entryBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.SW, argReg, MipsRegister.SP, offset));
            } else {
                // 超过4个的参数由调用者存放在调用者栈帧的 (i-4)*4 偏移处
                // 被调用者需要从 frameSize + (i-4)*4 处读取 (因为 SP 已经下移了 frameSize)
                int callerOffset = frameSize + (i - 4) * 4;

                // 1. 从栈中取出参数值 -> $t0
                entryBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.LW, MipsRegister.T0, MipsRegister.SP, callerOffset));

                // 2. 存入当前栈帧分配的局部变量位置 -> offset($sp)
                entryBlock.addInstruction(new MipsLoadStore(MipsLoadStore.Type.SW, MipsRegister.T0, MipsRegister.SP, offset));
            }
        }

        // 3.4 跳转到函数体的第一个基本块
        // OptimizedInstTranslator 会从 LLVM 的 entry 块开始创建 MIPS 块
        // 我们需要从 Prologue 跳转过去
        if (!func.getBasicBlocks().isEmpty()) {
            BasicBlock firstLLVMBlock = func.getBasicBlocks().get(0);
            String firstBlockLabel = funcName + "_" + firstLLVMBlock.getName();
            entryBlock.addInstruction(new MipsBranch("j", firstBlockLabel));
        }

        // [修改] 4. 翻译函数体 (根据优化等级选择翻译器)
        // 注意：这里需要引入 Config 类，或者直接硬编码 (这里假设 Config 在 util 包下)
        if (io.github.tomorrow615.compiler.util.Config.MIPS_OPTIMIZATION_LEVEL >= 1) {
            OptimizedInstTranslator translator = new OptimizedInstTranslator(stackManager, mipsFunc);
            translator.translate(func);
        } else {
            InstTranslator translator = new InstTranslator(stackManager, mipsFunc);
            translator.translate(func);
        }
    }
}
