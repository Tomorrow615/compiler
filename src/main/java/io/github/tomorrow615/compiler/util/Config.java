package io.github.tomorrow615.compiler.util;

public class Config {
    // ==================== 输出配置 ====================
    public static final boolean ENABLE_LEXER_OUTPUT = false;
    public static final boolean ENABLE_PARSER_OUTPUT = false;
    public static final boolean ENABLE_SYMBOL_OUTPUT = false;
    public static final boolean ENABLE_LLVM_IR_OUTPUT = true;

    // ==================== 优化总开关 ====================
    // 是否开启LLVM IR优化
    public static final boolean OPTIMIZE_LLVM = true;
    
    // 激进优化模式（启用：循环展开、数组全局变量本地化等）
    public static boolean AGGRESSIVE_MODE = true;

    // ==================== 中端优化开关 ====================
    // --- 预处理阶段 (Phase 1) ---
    public static boolean OPT_SROA = true;           // 数组标量替换
    public static boolean OPT_GLOBAL2LOCAL = true;   // 全局变量本地化
    public static boolean OPT_MEM2REG = true;        // SSA转换（内存到寄存器）
    public static boolean OPT_SIMPLIFY_CFG = true;   // CFG简化
    
    // --- 核心迭代阶段 (Phase 2) ---
    public static boolean OPT_INLINING = true;       // 函数内联
    public static boolean OPT_GVN = true;            // 全局值编号（公共子表达式消除）
    public static boolean OPT_CONST_FOLDING = true;  // 常量折叠
    public static boolean OPT_DCE = true;            // 死代码删除
    public static boolean OPT_ARITHMETIC = true;     // 算术优化（乘除法优化）
    public static boolean OPT_GLOBAL_DCE = true;     // 全局死函数消除
    
    // --- 收尾阶段 (Phase 3) ---
    public static boolean OPT_ALGEBRAIC = true;      // 代数简化
    public static boolean OPT_LICM = true;           // 循环不变量外提
    public static boolean OPT_STRENGTH_REDUCE = true;// 强度削减

    // ==================== 优化阈值参数 ====================
    // --- 迭代控制参数 ---
    public static int MAX_INLINE_ITERATIONS = 15;    // 最大内联迭代次数
    public static int MAX_INNER_ITERATIONS = 3;      // 内部清理循环次数
    public static int MAX_INSTRUCTION_THRESHOLD = 1000; // 代码膨胀熔断阈值
    
    // --- 数组处理参数 ---
    // 数组大小限制：超过此大小的数组不会被 SROA 拆解或 Global2Local 本地化
    public static int MAX_ARRAY_SIZE = 1024;
    
    // --- 循环展开参数 ---
    // 循环最大迭代次数：只有迭代次数 <= 此值的循环才会被完全展开
    public static int MAX_TRIP_COUNT = 64;
    
    // 循环展开后最大指令数：展开后指令总数超过此值则放弃展开
    public static int MAX_UNROLL_INSTRUCTIONS = 4000;
    
    // --- 函数内联参数 ---
    // 被调函数最大指令数：只有指令数 < 此值的函数才会被内联
    public static int INLINE_THRESHOLD = 100;
    
    // 调用者最大指令数：函数体积超过此值则停止向其内联
    public static int MAX_CALLER_SIZE = 10000;

    // ==================== 后端配置 ====================
    // 是否生成 MIPS 代码
    public static final boolean GENERATE_MIPS = true;
    
    // MIPS 优化等级 (0=无优化, 1=寄存器分配优化)
    public static final int MIPS_OPTIMIZATION_LEVEL = 1;
    
    // ==================== 自定义优化开关 ====================
    // Phase 1: 后端立即数优化 (用 addiu 替换 li + addu)
    public static boolean ENABLE_BACKEND_IMM_OPT = true;

    // Phase 2: Main函数开销消除 (不保存 $ra/$s)
    public static boolean ENABLE_MAIN_NO_STACK = true;

    // 是否开启基本块布局优化（减少无条件跳转）
    public static boolean ENABLE_BLOCK_LAYOUT = true;
}
