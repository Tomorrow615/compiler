package io.github.tomorrow615.compiler.util;

public class Config {
    // 词法分析
    public static final boolean ENABLE_LEXER_OUTPUT = false;
    // 语法分析
    public static final boolean ENABLE_PARSER_OUTPUT = false;
    // 语义分析
    public static final boolean ENABLE_SYMBOL_OUTPUT = false;
    // LLVM IR
    public static final boolean ENABLE_LLVM_IR_OUTPUT = false;

    // 是否开启LLVM IR优化
    public static final boolean OPTIMIZE_LLVM = false;
    // 是否生成 MIPS 代码
    public static final boolean GENERATE_MIPS = true;
    // MIPS 优化等级
    public static final int MIPS_OPTIMIZATION_LEVEL = 0;
}
