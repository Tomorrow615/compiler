package io.github.tomorrow615.compiler.backend.allocator;

import io.github.tomorrow615.compiler.backend.mips.MipsModule;
import io.github.tomorrow615.compiler.backend.mips.MipsRegister;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsInstruction;
import io.github.tomorrow615.compiler.backend.mips.operand.Operand;
import io.github.tomorrow615.compiler.backend.mips.operand.VirtualRegister;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsBasicBlock;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;

import java.util.HashMap;
import java.util.Map;

/**
 * 傻瓜式寄存器分配器 (用于验证基础设施)
 * 逻辑：不进行任何分析，将 VirtualRegister 按顺序映射到 $t0-$t9, $s0-$s7。
 * 注意：如果变量超过物理寄存器数量，会抛出异常。
 */
public class NaiveAllocator {
    private final MipsModule module;

    public NaiveAllocator(MipsModule module) {
        this.module = module;
    }

    public void allocate() {
        for (MipsFunction func : module.getFunctions()) {
            allocateFunction(func);
        }
    }

    private void allocateFunction(MipsFunction func) {
        // 虚拟寄存器 -> 物理寄存器 映射表
        Map<VirtualRegister, MipsRegister> mapping = new HashMap<>();
        
        // 简单的可用寄存器池
        MipsRegister[] pool = {
            MipsRegister.T0, MipsRegister.T1, MipsRegister.T2, MipsRegister.T3,
            MipsRegister.T4, MipsRegister.T5, MipsRegister.T6, MipsRegister.T7,
            MipsRegister.T8, MipsRegister.T9,
            MipsRegister.S0, MipsRegister.S1, MipsRegister.S2, MipsRegister.S3,
            MipsRegister.S4, MipsRegister.S5, MipsRegister.S6, MipsRegister.S7,
            MipsRegister.K0, MipsRegister.K1 // 实在不行把 K0 K1 也用了
        };
        
        int usedCount = 0;

        for (MipsBasicBlock block : func.getBlocks()) {
            for (MipsInstruction inst : block.getInstructions()) {
                
                // 1. 检查並替换 Use
                for (Operand use : inst.getUse()) {
                    if (use instanceof VirtualRegister vReg) {
                        if (!mapping.containsKey(vReg)) {
                            if (usedCount >= pool.length) {
                                throw new RuntimeException("NaiveAllocator: Registers exhausted in function " + func.getName());
                            }
                            mapping.put(vReg, pool[usedCount++]);
                        }
                        inst.replaceUse(vReg, mapping.get(vReg));
                    }
                }
                
                // 2. 检查並替换 Def
                for (Operand def : inst.getDef()) {
                    if (def instanceof VirtualRegister vReg) {
                        if (!mapping.containsKey(vReg)) {
                            if (usedCount >= pool.length) {
                                throw new RuntimeException("NaiveAllocator: Registers exhausted in function " + func.getName());
                            }
                            mapping.put(vReg, pool[usedCount++]);
                        }
                        inst.replaceDef(vReg, mapping.get(vReg));
                    }
                }
            }
        }
    }
}
