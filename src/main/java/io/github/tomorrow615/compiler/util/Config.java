package io.github.tomorrow615.compiler.util;

public class Config {
    // ==================== 输出配置 ====================
    public static final boolean ENABLE_LEXER_OUTPUT = false;
    public static final boolean ENABLE_PARSER_OUTPUT = false;
    public static final boolean ENABLE_SYMBOL_OUTPUT = false;
    public static final boolean ENABLE_LLVM_IR_OUTPUT = true;
    public static final boolean GENERATE_MIPS = true;

    // ==================== 优化总开关 ====================
    public static final boolean OPTIMIZE_LLVM = true;

    // ==================== 中端优化开关 ====================
        
    // --- Phase 1: 预处理与清理 ---
    public static boolean OPT_SROA = true;                  // 数组标量替换
    public static boolean OPT_PROMOTE_STATIC_LOCAL = true;  // 静态局部变量提升 (全局常量传播)
    public static boolean OPT_GLOBAL2LOCAL = true;          // 全局变量本地化 (含 main 函数激进优化)
    public static boolean OPT_MEM2REG = true;               // SSA转换
    public static boolean OPT_SIMPLIFY_CFG = true;          // CFG简化
    public static boolean OPT_DCE = true;                   // 死代码消除
    public static boolean OPT_GLOBAL_DCE = true;            // 全局死函数消除

    // --- Phase 2: 核心迭代优化 ---
    public static boolean OPT_INLINING = true;              // 函数内联
    public static boolean OPT_GVN = true;                   // 全局值编号 (GVN)
    public static boolean OPT_CONST_FOLDING = true;         // 常量折叠
    public static boolean OPT_ARITHMETIC = true;            // 算术优化
    public static boolean OPT_LOOP_UNROLL = true;           // 循环展开

    // --- Phase 3: 收尾与高级优化 ---
    public static boolean OPT_ALGEBRAIC = true;             // 代数简化
    public static boolean OPT_LICM = true;                  // 循环不变量外提
    public static boolean OPT_STRENGTH_REDUCE = true;       // 强度削减
    
    // ==================== 优化阈值参数 ====================
    
    // --- 迭代控制 ---
    public static int MAX_INLINE_ITERATIONS = 10;           // 最大内联迭代次数
    public static int MAX_INNER_ITERATIONS = 3;             // 内部清理循环次数
    public static int MAX_INSTRUCTION_THRESHOLD = 2000;     // 代码膨胀熔断阈值(容易tle）

    // --- 函数内联 ---
    public static int MAX_INLINES_PER_ROUND = 50;           // 单轮内联最大数量
    public static int INLINE_THRESHOLD = 300;               // 被调函数最大指令数
    public static int MAX_CALLER_SIZE = 6000;               // 调用者最大指令数限制

    // --- 循环展开 ---
    public static int MAX_TRIP_COUNT = 200;                 // 最大展开迭代次数
    public static int MAX_UNROLL_INSTRUCTIONS = 5000;       // 展开后最大指令数限制
    
    // --- 数组优化 ---
    public static int MAX_ARRAY_SIZE = 1024;                // SROA/Global2Local 数组大小限制

    // ==================== 后端优化开关 ====================
    
    // --- 立即数优化 ---
    public static boolean ENABLE_BACKEND_IMM_OPT = true;

    // --- Main函数优化 ---
    public static boolean ENABLE_MAIN_NO_STACK = true;

    // --- 基本块布局优化 ---
    public static boolean ENABLE_BLOCK_LAYOUT = true;
    
    // 激进窥孔优化开关
    // 包含：跨指令转发、分支跳转增强、栈指针合并、Move链消除等高级优化
    // 风险较高，但能显著降低 FinalCycle
    public static boolean ENABLE_AGGRESSIVE_PEEPHOLE = true;
    
    // [Extreme] 极端激进优化 (Assumption-based Aliasing, etc.)
    public static boolean ENABLE_EXTREME_PEEPHOLE = true;
}
