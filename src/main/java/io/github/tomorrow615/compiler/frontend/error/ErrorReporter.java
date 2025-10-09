package io.github.tomorrow615.compiler.frontend.error;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ErrorReporter {
    private static final List<Error> errors = new ArrayList<>();

    /**
     * 添加一条新的错误记录.
     * @param lineNumber 错误所在的行号
     * @param errorCode 错误码
     */
    public static void addError(int lineNumber, char errorCode) {
        errors.add(new Error(lineNumber, errorCode));
    }

    /**
     * 检查是否存在错误.
     * @return 如果有错误则返回 true
     */
    public static boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * 获取所有已记录的错误，并按行号排序.
     * @return 排序后的错误列表
     */
    public static List<Error> getErrors() {
        Collections.sort(errors);
        return errors;
    }

    /**
     * 清空所有错误记录，用于多次独立运行 (例如测试).
     */
    public static void clearErrors() {
        errors.clear();
    }
}