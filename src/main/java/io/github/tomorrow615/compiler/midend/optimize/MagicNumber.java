package io.github.tomorrow615.compiler.midend.optimize;

import java.math.BigInteger;

/**
 * Magic Number 计算工具类 (Fixed Version)
 * * 算法来源：LLVM APInt::magic() / Hacker's Delight
 * 作用：计算 m (multiplier) 和 s (shift)，使得 x / d == (mulhs(x, m) + (needsAdd ? x : 0)) >> s
 */
public class MagicNumber {

    public static class MagicResult {
        public final int multiplier;   // 32位乘数
        public final int shift;        // 移位量
        public final boolean needsAdd; // 是否需要 add 修正 (对应 mulhs + add)

        public MagicResult(int multiplier, int shift, boolean needsAdd) {
            this.multiplier = multiplier;
            this.shift = shift;
            this.needsAdd = needsAdd;
        }
        
        @Override
        public String toString() {
            return String.format("Magic(mul=%d, shift=%d, add=%b)", multiplier, shift, needsAdd);
        }
    }

    /**
     * 计算有符号除法的 Magic Number
     * @param divisor 除数 (必须 > 0)
     */
    public static MagicResult computeSigned(int divisor) {
        // 1. 边界检查
        if (divisor <= 0 || (divisor & (divisor - 1)) == 0) {
            return null; // 负数或2的幂次不由本算法处理
        }

        // 2. 初始化常量
        // 这里完全按照 LLVM 的逻辑，使用 BigInteger 模拟无符号 64 位/更多位计算
        BigInteger d = BigInteger.valueOf(divisor);
        BigInteger two31 = BigInteger.ONE.shiftLeft(31); // 2^31
        
        // anc = 2^31 - 1 - (2^31 - 1) % d
        // 也就是 LLVM 中的: delta = d - 1 - (2^31 + d - 1) % d ? 不，直接看 Hacker's Delight 公式
        // LLVM 逻辑:
        // ad = abs(d) -> 这里 d 已经是正数
        // t = 2^31 + (d >> 31) -> 也就是 2^31
        // anc = t - 1 - (t % d)
        BigInteger t = two31;
        BigInteger anc = t.subtract(BigInteger.ONE).subtract(t.mod(d));
        
        int p = 31; // 初始化 p = 31
        BigInteger q1, r1, q2, r2, delta;
        
        BigInteger one = BigInteger.ONE;
        
        // 3. 搜索循环
        while (true) {
            p++;
            // twoP = 2^p
            BigInteger twoP = one.shiftLeft(p);
            
            // q1 = 2^p / anc
            // r1 = 2^p % anc
            q1 = twoP.divide(anc);
            r1 = twoP.mod(anc);
            
            // q2 = 2^p / d
            // r2 = 2^p % d
            q2 = twoP.divide(d);
            r2 = twoP.mod(d);
            
            // delta = d - r2
            delta = d.subtract(r2);
            
            // 循环继续条件: q1 < delta || (q1 == delta && r1 == 0)
            if (q1.compareTo(delta) < 0 || (q1.equals(delta) && r1.equals(BigInteger.ZERO))) {
                continue;
            } else {
                break;
            }
        }
        
        // 4. 计算最终结果
        // mag = q2 + 1
        BigInteger mag = q2.add(one);
        int shift = p - 32;
        
        // 5. 判断是否需要 Add 修正
        // 逻辑：如果 mag >= 2^31，说明它在 32 位有符号整数里看起来是负数
        // 这时需要使用 mulhs + add 算法
        boolean needsAdd = mag.compareTo(two31) >= 0;
        
        // 截断为 32 位整数 (即使是负数也没关系，底层位模式是对的)
        int multiplier = mag.intValue();
        
        return new MagicResult(multiplier, shift, needsAdd);
    }
}
