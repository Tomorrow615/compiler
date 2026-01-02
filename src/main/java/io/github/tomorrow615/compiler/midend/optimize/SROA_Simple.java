package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.instruction.*;
import io.github.tomorrow615.compiler.midend.llvm.type.ArrayType;
import io.github.tomorrow615.compiler.midend.llvm.type.IntegerType;
import io.github.tomorrow615.compiler.midend.llvm.type.Type;
import io.github.tomorrow615.compiler.midend.llvm.value.*;
import io.github.tomorrow615.compiler.util.Config;

import java.util.*;

/**
 * 简易版 SROA (Scalar Replacement of Aggregates)
 * 目标：将小的、固定大小的、只通过常量下标访问的局部数组，拆解为多个独立的 i32 变量。
 * 作用：拆解后，Mem2Reg 可以将这些变量提升到寄存器，消除 MEM 代价。
 */
public class SROA_Simple implements Pass {

    @Override
    public String getName() {
        return "SROA_Simple";
    }

    @Override
    public void runOnFunction(Function func) {
        if (func.getBasicBlocks().isEmpty()) return;
        
        BasicBlock entry = func.getBasicBlocks().get(0);
        // 复制列表，避免迭代时修改
        List<Instruction> instructions = new ArrayList<>(entry.getInstructions());
        
        for (Instruction inst : instructions) {
            if (inst instanceof AllocaInst alloca) {
                tryPromoteArray(alloca, entry);
            }
        }
    }

    private void tryPromoteArray(AllocaInst alloca, BasicBlock entryBlock) {
        Type type = alloca.getAllocatedType();
        
        // 1. 必须是数组类型
        if (!(type instanceof ArrayType arrayType)) {
            return;
        }
        
        // 2. 只能是一维数组（元素不能再是数组）
        if (arrayType.getElementType() instanceof ArrayType) {
            return; // 暂不处理多维数组
        }
        
        // 3. 元素必须是 i32
        if (!arrayType.getElementType().equals(IntegerType.i32)) {
            return;
        }
        
        // 4. 数组大小不能太大
        int size = arrayType.getNumElements();
        if (size > Config.MAX_ARRAY_SIZE || size <= 0) {
            return; 
        }

        // 5. 检查所有用途：必须是 GEP 指令，且下标必须是常数
        // 【关键修复】同时检查数组是否逃逸（被传递给函数、被其他指令使用等）
        List<GetElementPtrInst> geps = new ArrayList<>();
        for (Use use : alloca.getUsers()) {
            User user = use.getUser();
            if (user instanceof GetElementPtrInst gep) {
                // SysY 中数组访问: gep ptr, i32 0, i32 index
                // operand(0) = ptr, operand(1) = 0, operand(2) = index
                if (gep.getOperands().size() != 3) {
                    return; // 格式不符
                }
                
                Value idx0 = gep.getOperand(1);
                Value idx1 = gep.getOperand(2);
                
                // 第一个索引必须是 0
                if (!(idx0 instanceof ConstantInt c0) || c0.getValue() != 0) {
                    return;
                }
                
                // 第二个索引必须是常量
                if (!(idx1 instanceof ConstantInt)) {
                    return;
                }
                
                // 【关键修复】检查 GEP 的使用者
                // GEP 结果只能用于直接的 Load/Store 数组元素
                for (Use gepUse : gep.getUsers()) {
                    User gepUser = gepUse.getUser();
                    if (gepUser instanceof StoreInst storeInst) {
                        // 区分：store value, gep（正常访问）vs store gep, ptr（指针逃逸）
                        if (storeInst.getValue() == gep) {
                            // GEP 指针本身被存储 → 数组地址逃逸！
                            return;
                        }
                        // store xxx, gep 是正常的元素访问，OK
                    } else if (!(gepUser instanceof LoadInst)) {
                        // GEP 被用于其他目的（如 CallInst 参数），数组逃逸了
                        return;
                    }
                }
                
                geps.add(gep);
            } else {
                // 【关键修复】alloca 直接被使用（不通过 GEP）
                // 这通常意味着数组作为指针被传递给了函数，禁止拆分！
                return;
            }
        }

        // === 开始拆解 ===
        
        // 1. 创建 N 个独立的 alloca i32
        AllocaInst[] splitAllocas = new AllocaInst[size];
        int insertIdx = entryBlock.getInstructions().indexOf(alloca);
        
        for (int i = 0; i < size; i++) {
            // 使用无 parentBlock 的构造函数，避免自动添加到末尾
            AllocaInst newAlloca = new AllocaInst(IntegerType.i32, alloca.getName() + "_" + i);
            newAlloca.setParentBlock(entryBlock);
            // 手动插入到指定位置
            entryBlock.getInstructions().add(insertIdx++, newAlloca);
            splitAllocas[i] = newAlloca;
        }

        // 2. 替换所有的 GEP
        for (GetElementPtrInst gep : geps) {
            ConstantInt idxVal = (ConstantInt) gep.getOperand(2);
            int index = idxVal.getValue();
            
            // 边界检查
            if (index >= 0 && index < size) {
                AllocaInst targetAlloca = splitAllocas[index];
                
                // 将所有使用 GEP 的地方（Load/Store）改为使用 targetAlloca
                // 复制一份，因为 setValue 会修改列表
                List<Use> gepUsers = new ArrayList<>(gep.getUsers());
                for (Use use : gepUsers) {
                    use.setValue(targetAlloca);
                }
                
                // GEP 现在应该没有使用者了，可以安全删除
                gep.removeUseFromOperands();
                gep.getParentBlock().getInstructions().remove(gep);
            }
        }

        // 3. 删除旧的数组 alloca
        alloca.removeUseFromOperands();
        entryBlock.getInstructions().remove(alloca);
    }
}
