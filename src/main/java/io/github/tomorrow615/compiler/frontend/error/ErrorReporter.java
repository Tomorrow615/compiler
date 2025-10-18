package io.github.tomorrow615.compiler.frontend.error;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class ErrorReporter {
    private static final List<Error> errors = new ArrayList<>();
    private static final Set<Integer> linesWithErrors = new HashSet<>();

    public static void addError(int lineNumber, char errorCode) {
        // 在添加错误前，先检查该行是否已经报过错
        if (linesWithErrors.contains(lineNumber)) {
            return; // 如果已经报过，则直接返回，不再添加新的错误
        }

        errors.add(new Error(lineNumber, errorCode));
        linesWithErrors.add(lineNumber); // 将行号加入集合，表示该行已报错
    }

    public static boolean hasErrors() {
        return !errors.isEmpty();
    }

    public static List<Error> getErrors() {
        Collections.sort(errors);
        return errors;
    }


    public static void clearErrors() {
        errors.clear();
        linesWithErrors.clear();
    }
}