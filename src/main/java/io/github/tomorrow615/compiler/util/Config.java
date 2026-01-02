package io.github.tomorrow615.compiler.util;

public class Config {
    // 词法分析
    public static final boolean ENABLE_LEXER_OUTPUT = false;
    // 语法分析
    public static final boolean ENABLE_PARSER_OUTPUT = false;
    // 语义分析
    public static final boolean ENABLE_SYMBOL_OUTPUT = false;
    // LLVM IR
    public static final boolean ENABLE_LLVM_IR_OUTPUT = true;

    // 是否开启LLVM IR优化（总开关）
    public static final boolean OPTIMIZE_LLVM = true;
    
    // tc7优化模式（控制：数组全局变量本地化 + 循环展开）
    public static boolean AGGRESSIVE_MODE = true;

    // --- 预处理阶段 (Phase 1) ---
    public static boolean OPT_SROA = true;           // 数组标量替换
    public static boolean OPT_GLOBAL2LOCAL = true;   // 标量全局变量本地化（叶子函数）
    public static boolean OPT_MEM2REG = true;        // SSA转换
    public static boolean OPT_SIMPLIFY_CFG = true;   // CFG简化
    
    // --- 核心迭代阶段 (Phase 2) ---
    public static boolean OPT_INLINING = true;       // 函数内联
    public static boolean OPT_GVN = true;            // 全局值编号
    public static boolean OPT_CONST_FOLDING = true;  // 常量折叠
    public static boolean OPT_DCE = true;            // 死代码删除
    public static boolean OPT_ARITHMETIC = true;     // 乘除法优化
    // 注：循环展开由 AGGRESSIVE_MODE 控制，无需单独开关
    public static boolean OPT_GLOBAL_DCE = true;     // 全局死函数消除
    
    // --- 收尾阶段 (Phase 3) ---
    public static boolean OPT_ALGEBRAIC = true;      // 代数简化
    public static boolean OPT_LICM = true;           // 循环不变量外提
    public static boolean OPT_STRENGTH_REDUCE = true;// 强度削减
    
    // --- 迭代参数 ---
    public static int MAX_INLINE_ITERATIONS = 15;    // 最大内联迭代次数
    public static int MAX_INNER_ITERATIONS = 3;      // 内部清理循环次数
    public static int MAX_INSTRUCTION_THRESHOLD = 1000; // 代码膨胀熔断阈值
    
    // ===== MIPS 后端配置 =====
    
    // 是否生成 MIPS 代码
    public static final boolean GENERATE_MIPS = true;
    // MIPS 优化等级 (0=无优化全栈式, 1=寄存器缓存优化)
    public static final int MIPS_OPTIMIZATION_LEVEL = 1;
}
