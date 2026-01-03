package io.github.tomorrow615.compiler.midend.optimize;

import io.github.tomorrow615.compiler.midend.llvm.Module;
import io.github.tomorrow615.compiler.midend.llvm.value.Constant;
import io.github.tomorrow615.compiler.midend.llvm.value.Function;
import io.github.tomorrow615.compiler.midend.llvm.value.GlobalVariable;
import io.github.tomorrow615.compiler.midend.llvm.value.Use;
import io.github.tomorrow615.compiler.midend.llvm.value.User;
import io.github.tomorrow615.compiler.midend.llvm.value.Value;
import io.github.tomorrow615.compiler.midend.llvm.instruction.LoadInst;
import io.github.tomorrow615.compiler.midend.llvm.instruction.StoreInst;

import java.util.ArrayList;
import java.util.List;

/**
 * PromoteStaticLocal Pass
 * 
 * 目标：优化具有 Internal Linkage（静态/私有）的全局变量。
 * 功能：
 * 1. Read-Only Promotion:
 *    如果一个 GlobalVariable 从未被 Store 覆盖（只有初始值），
 *    则不仅它是只读的，而且它是编译时常量。
 *    将所有 Load 替换为 GlobalVariable.getInitializer()。
 * 
 * 2. Unused Global Elimination:
 *    如果一个 GlobalVariable 的所有 User 都是 Store（意味着只写不读），
 *    或者没有 User，且该变量是 internal 的，则可以安全删除。
 */
public class PromoteStaticLocal implements Pass {

    @Override
    public String getName() {
        return "PromoteStaticLocal";
    }

    @Override
    public void runOnFunction(Function function) {
        // 这是一个 Module 级别的优化，runOnModule 已经覆盖了。
    }

    @Override
    public void runOnModule(Module module) {
        boolean changed = true;
        while (changed) {
            changed = false;
            List<GlobalVariable> globals = new ArrayList<>(module.getGlobalVariables()); 
            
            for (GlobalVariable g : globals) {
                if (g.getInitializer() == null) continue;
                if (isAddressEscaped(g)) continue;

                List<LoadInst> loads = new ArrayList<>();
                List<StoreInst> stores = new ArrayList<>();
                boolean complexUse = false;

                for (Use use : g.getUsers()) {
                    User user = use.getUser();
                    if (user instanceof LoadInst) {
                        loads.add((LoadInst) user);
                    } else if (user instanceof StoreInst) {
                        StoreInst store = (StoreInst) user;
                        // 逻辑：如果是写入到 g，则记录
                        if (store.getPointer() == g) {
                            stores.add(store);
                        } else {
                            complexUse = true; 
                        }
                    } else {
                        complexUse = true; 
                    }
                }

                if (complexUse) continue;

                // 优化 1: 只读变量常量化
                if (stores.isEmpty()) {
                    if (!loads.isEmpty()) {
                        Constant initVal = g.getInitializer();
                        for (LoadInst load : loads) {
                            load.replaceAllUsesWith(initVal);
                            load.remove(); 
                        }
                        changed = true;
                    }
                }

                // 优化 2: 只写变量或无用变量消除
                if (loads.isEmpty()) {
                    for (StoreInst store : stores) {
                        store.remove();
                    }
                    module.removeGlobalVariable(g);
                    changed = true;
                }
            }
        }
    }

    private boolean isAddressEscaped(GlobalVariable g) {
        for (Use use : g.getUsers()) {
            User user = use.getUser();
            if (!(user instanceof LoadInst) && !(user instanceof StoreInst)) {
                return true;
            }
            if (user instanceof StoreInst) {
                if (((StoreInst) user).getValue() == g) return true;
            }
        }
        return false;
    }
}
