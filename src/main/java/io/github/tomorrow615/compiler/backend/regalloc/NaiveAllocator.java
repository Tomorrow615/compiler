package io.github.tomorrow615.compiler.backend.regalloc;

import io.github.tomorrow615.compiler.backend.mips.MipsModule;
import io.github.tomorrow615.compiler.backend.mips.MipsRegister;
import io.github.tomorrow615.compiler.backend.mips.assembly.MipsInstruction;
import io.github.tomorrow615.compiler.backend.mips.operand.Operand;
import io.github.tomorrow615.compiler.backend.mips.operand.VirtualRegister;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsBasicBlock;
import io.github.tomorrow615.compiler.backend.mips.structure.MipsFunction;

import java.util.List;

/**
 * 朴素寄存器分配器 (Step 1.4)
 * 用于验证前端净化 (Frontend Purification) 的正确性。
 * 策略：简单的 Modulo Mapping，不考虑活跃区间分析。
 */
public class NaiveAllocator {
    private final MipsModule module;

    // 可用的物理寄存器池
    // 使用 $t0-$t9, $s0-$s7 (共 18 个)
    private static final MipsRegister[] POOL = {
        MipsRegister.T0, MipsRegister.T1, MipsRegister.T2, MipsRegister.T3,
        MipsRegister.T4, MipsRegister.T5, MipsRegister.T6, MipsRegister.T7,
        MipsRegister.T8, MipsRegister.T9,
        MipsRegister.S0, MipsRegister.S1, MipsRegister.S2, MipsRegister.S3,
        MipsRegister.S4, MipsRegister.S5, MipsRegister.S6, MipsRegister.S7
    };

    public NaiveAllocator(MipsModule module) {
        this.module = module;
    }

    public void allocate() {
        for (MipsFunction func : module.getFunctions()) {
            allocateFunction(func);
        }
    }

    private void allocateFunction(MipsFunction func) {
        for (MipsBasicBlock block : func.getBlocks()) {
            for (MipsInstruction inst : block.getInstructions()) {
                // 替换 Def
                List<Operand> defs = inst.getDef();
                for (Operand def : defs) {
                    if (def instanceof VirtualRegister vreg) {
                        MipsRegister phys = mapToPhysical(vreg);
                        inst.replaceDef(vreg, phys);
                    }
                }

                // 替换 Use
                List<Operand> uses = inst.getUse();
                for (Operand use : uses) {
                    if (use instanceof VirtualRegister vreg) {
                        MipsRegister phys = mapToPhysical(vreg);
                        inst.replaceUse(vreg, phys);
                    }
                }
            }
        }
    }

    private MipsRegister mapToPhysical(VirtualRegister vreg) {
        // 简单的模运算映射
        int index = Math.abs(vreg.getId()) % POOL.length;
        return POOL[index];
    }
}
