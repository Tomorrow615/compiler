package io.github.tomorrow615.compiler.backend.regalloc;

import io.github.tomorrow615.compiler.backend.mips.MipsModule;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsInstruction;
import io.github.tomorrow615.compiler.backend.mips.operand.Operand;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsBasicBlock;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 活跃变量分析
 * 计算每个基本块的 Use/Def 集合，以及 LiveIn/LiveOut 集合
 */
public class LivenessAnalyzer {
    private final MipsModule module;

    public LivenessAnalyzer(MipsModule module) {
        this.module = module;
    }

    public void analyze() {
        for (MipsFunction func : module.getFunctions()) {
            analyzeFunction(func);
        }
    }

    public void analyzeFunction(MipsFunction func) {
        // 1. 计算 Use 和 Def 集合
        for (MipsBasicBlock block : func.getBlocks()) {
            computeUseDef(block);
        }

        // 2. 计算 LiveIn 和 LiveOut
        computeLiveInOut(func);
    }

    /**
     * 计算基本块的 Use 和 Def 集合
     * Use[B]: 在块 B 中被使用且在使用前未被 B 定义的变量
     * Def[B]: 在块 B 中被定义且在定义前未被使用的变量 (严格来说是 Killed 集合)
     * 更准确的定义:
     * - Use[B]: 向上暴露的使用 (Upward Exposed Uses)
     * - Def[B]: 块内定义的变量 (Killed Definitions)
     */
    private void computeUseDef(MipsBasicBlock block) {
        Set<Operand> use = block.getUse();
        Set<Operand> def = block.getDef();
        use.clear();
        def.clear();

        for (MipsInstruction inst : block.getInstructions()) {
            // 检查 Use
            for (Operand op : inst.getUse()) {
                // 如果该变量尚未在这个块中被定义 (即不在 def 集合中)
                // 那么这是一个“向上暴露的使用”，加入 use 集合
                if (!def.contains(op)) {
                    use.add(op);
                }
            }

            // 检查 Def
            for (Operand op : inst.getDef()) {
                // 加入 def 集合 (Kill)
                def.add(op);
            }
        }
    }

    /**
     * 迭代计算 LiveIn 和 LiveOut 集合
     * 
     * LiveIn[B] = Use[B] U (LiveOut[B] - Def[B])
     * LiveOut[B] = U (LiveIn[S]), 其中 S 是 B 的后继
     */
    private void computeLiveInOut(MipsFunction func) {
        List<MipsBasicBlock> blocks = func.getBlocks();
        boolean changed = true;
        
        while (changed) {
            changed = false;
            
            // 逆序遍历有助于加速收敛
            for (int i = blocks.size() - 1; i >= 0; i--) {
                MipsBasicBlock block = blocks.get(i);
                
                // 1. 计算 LiveOut = Union(LiveIn[S])
                Set<Operand> newLiveOut = new HashSet<>();
                for (MipsBasicBlock succ : block.getSuccessors()) {
                    newLiveOut.addAll(succ.getLiveIn());
                }
                
                // 2. 计算 LiveIn = Use U (LiveOut - Def)
                Set<Operand> newLiveIn = new HashSet<>(newLiveOut);
                newLiveIn.removeAll(block.getDef());
                newLiveIn.addAll(block.getUse());
                
                // 3. 检查是否有变化
                if (!newLiveIn.equals(block.getLiveIn()) || !newLiveOut.equals(block.getLiveOut())) {
                    changed = true;
                    block.getLiveIn().clear();
                    block.getLiveIn().addAll(newLiveIn);
                    
                    block.getLiveOut().clear();
                    block.getLiveOut().addAll(newLiveOut);
                }
            }
        }
    }
}
