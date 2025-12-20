package io.github.tomorrow615.compiler.midend.optimize;

/**
 * Magic Number 计算工具类
 * 
 * 用于将有符号整数除法转换为乘法+移位运算
 * 原理：x / d ≈ (x * m) >> (32 + s)
 * 
 * 参考：Hacker's Delight, Chapter 10
 */
public class MagicNumber {
    
    /**
     * Magic Number 计算结果
     */
    public static class MagicResult {
        public final long multiplier;  // 乘数 m
        public final int shift;        // 移位量 s
        public final boolean needsAdd; // 是否需要加法修正
        
        public MagicResult(long multiplier, int shift, boolean needsAdd) {
            this.multiplier = multiplier;
            this.shift = shift;
            this.needsAdd = needsAdd;
        }
    }
    
    /**
     * 计算有符号除法的 Magic Number
     * 
     * @param divisor 除数（必须为正整数，且不是 2 的幂次）
     * @return Magic Number 结果，如果无法优化返回 null
     */
    public static MagicResult computeSigned(int divisor) {
        if (divisor <= 0) {
            return null;
        }
        
        // 2 的幂次用简单移位处理，不需要 Magic Number
        if (isPowerOfTwo(divisor)) {
            return null;
        }
        
        // 使用简化的 Magic Number 算法
        // 参考：https://gmplib.org/~tege/divcnst-pldi94.pdf
        
        int d = divisor;
        int p = 31; // 初始移位量
        
        // 计算 2^(32+p) / d
        long twoP32 = 1L << 32;
        long numerator = twoP32 << p;
        long m = numerator / d;
        long r = numerator % d;
        
        // 调整以获得正确的舍入
        // m = ceil(2^(32+p) / d)
        if (r > 0) {
            m++;
        }
        
        // 检查乘数是否溢出 32 位
        // 如果溢出，使用带加法修正的版本
        boolean needsAdd = false;
        if (m >= (1L << 32)) {
            // 需要使用 mulhs + add 方案
            m = m - (1L << 32);
            needsAdd = true;
        }
        
        // 优化：减少移位量
        while (p > 0 && (m & 1) == 0) {
            m >>= 1;
            p--;
        }
        
        return new MagicResult(m, p, needsAdd);
    }
    
    /**
     * 计算无符号除法的 Magic Number (简化版)
     */
    public static MagicResult computeUnsigned(int divisor) {
        if (divisor <= 0) {
            return null;
        }
        
        if (isPowerOfTwo(divisor)) {
            return null;
        }
        
        // 简化实现：使用固定的 32 位移位
        long d = divisor & 0xFFFFFFFFL;
        long m = ((1L << 32) + d - 1) / d; // ceil(2^32 / d)
        
        return new MagicResult(m, 0, false);
    }
    
    private static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
}
