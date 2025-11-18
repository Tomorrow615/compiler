#!/bin/bash

# --- 1. 配置路径 (WSL 路径) ---
PROJECT_DIR="/mnt/e/compiler"
INPUT_FILE="$PROJECT_DIR/input.txt"
REFERENCE_C_FILE="$PROJECT_DIR/testfile.txt"
SYLIB_C_FILE="$PROJECT_DIR/sylib.c"
SYLIB_O_FILE="$PROJECT_DIR/sylib.o"
MY_LLVM_IR="$PROJECT_DIR/llvm_ir.txt"
REFERENCE_RESULT_TXT="$PROJECT_DIR/reference_result.txt"
LLVM_RESULT_TXT="$PROJECT_DIR/llvm_result.txt"
CHECK_TXT="$PROJECT_DIR/check.txt"

# --- 颜色 ---
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

# --- 2. [修改] 只清理脚本的输出文件 ---
rm -f "$REFERENCE_RESULT_TXT" \
      "$LLVM_RESULT_TXT" \
      "$CHECK_TXT"

echo "--- [WSL] 开始自动化测试 ---"

# --- 3. 准备工作 (编译参考程序和运行时库) ---
# [修改] 安静执行：在后台执行，只在出错时停止脚本
clang -x c -Wno-implicit-function-declaration "$REFERENCE_C_FILE" "$SYLIB_C_FILE" -o "$PROJECT_DIR/reference_exe" &> /dev/null
if [ $? -ne 0 ]; then
    echo -e "${RED}--- 准备失败: 编译参考程序 C 文件时出错! ---${NC}"
    exit 1
fi

clang -c "$SYLIB_C_FILE" -o "$SYLIB_O_FILE" &> /dev/null
if [ $? -ne 0 ]; then
    echo -e "${RED}--- 准备失败: 编译运行时库 sylib.c 时出错! ---${NC}"
    exit 1
fi

# --- 4. 步骤一：验证 LLVM IR 语法 ---
echo -n "--- [1/2] 正在验证 LLVM IR 语法 (llvm-as)... "
LLVM_AS_ERROR=$({ llvm-as "$MY_LLVM_IR" -o /dev/null; } 2>&1)
if [ $? -ne 0 ]; then
    echo -e "${RED}[FAIL]${NC}"
    echo -e "${RED}--- 详细错误已保存到: $CHECK_TXT ---${NC}"
    echo "--- LLVM IR 语法验证失败 ---" > "$CHECK_TXT"
    echo "llvm-as 报告了以下错误 (通常会指出错误行号):" >> "$CHECK_TXT"
    echo "$LLVM_AS_ERROR" >> "$CHECK_TXT"

    rm -f "$PROJECT_DIR/reference_exe" "$SYLIB_O_FILE"
    exit 1
fi
echo -e "${GREEN}[OK]${NC}"

# --- 5. 步骤二：编译、运行并对拍 ---
echo -n "--- [2/2] 正在编译、运行并对拍... "
# 编译你的 LLVM IR (安静执行)
CLANG_ERROR=$(clang -x ir -Wno-override-module "$MY_LLVM_IR" -x none "$SYLIB_O_FILE" -o "$PROJECT_DIR/my_exe" 2>&1)
if [ $? -ne 0 ]; then
    echo -e "${RED}[FAIL]${NC}"
    echo -e "${RED}--- Clang 编译 IR 时失败 (崩溃或出错)，详细信息已保存到: $CHECK_TXT ---${NC}"
    echo "Clang 编译失败。这通常意味着你的 IR 存在语义错误（例如错误的基本块终结符）。" > "$CHECK_TXT"
    echo "--- Clang 错误信息: ---" >> "$CHECK_TXT"
    echo "$CLANG_ERROR" >> "$CHECK_TXT"

    rm -f "$PROJECT_DIR/reference_exe" "$SYLIB_O_FILE"
    exit 1
fi

# 运行两个程序 (安静执行)
eval "$PROJECT_DIR/reference_exe" < "$INPUT_FILE" > "$REFERENCE_RESULT_TXT"
REFERENCE_EXIT_CODE=$?
eval "$PROJECT_DIR/my_exe" < "$INPUT_FILE" > "$LLVM_RESULT_TXT"
MY_EXIT_CODE=$?

# 对拍 (Diff)
DIFF_OUTPUT=$(diff -w "$REFERENCE_RESULT_TXT" "$LLVM_RESULT_TXT")

if [ "$REFERENCE_EXIT_CODE" -eq "$MY_EXIT_CODE" ] && [ -z "$DIFF_OUTPUT" ]; then
    echo -e "${GREEN}[PASS]${NC}"
    echo -e "${GREEN}--- 测试通过。返回值和屏幕输出均一致。 ---${NC}"
    # 成功了，check.txt 保持不存在
else
    echo -e "${RED}[FAIL]${NC}"
    echo -e "${RED}--- 测试失败。详细对比报告已生成: $CHECK_TXT ---${NC}"

    # 将失败报告写入 $CHECK_TXT
    {
        echo "--- 对拍失败 ---"
        echo ""
        echo "--- 返回值对比 ---"
        if [ "$REFERENCE_EXIT_CODE" -eq "$MY_EXIT_CODE" ]; then
            echo "[PASS] 返回值一致: $MY_EXIT_CODE"
        else
            echo "[FAIL] 返回值不一致:"
            echo "  参考程序: $REFERENCE_EXIT_CODE"
            echo "  你的程序: $MY_EXIT_CODE"
        fi
        echo ""
        echo "--- 屏幕输出对比 (diff -w) ---"
        if [ -z "$DIFF_OUTPUT" ]; then
            echo "[PASS] 屏幕输出一致。"
        else
            echo "[FAIL] 屏幕输出不一致:"
            echo "$DIFF_OUTPUT"
        fi
    } > "$CHECK_TXT"
fi

# --- 6. 清理 ---
rm -f "$PROJECT_DIR/reference_exe" "$PROJECT_DIR/my_exe" "$SYLIB_O_FILE"
echo "--- [WSL] 测试结束 ---"