package io.github.tomorrow615.compiler.frontend.error;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ErrorReporter {
    private static final List<Error> errors = new ArrayList<>();

    public static void addError(int lineNumber, char errorCode) {
        errors.add(new Error(lineNumber, errorCode));
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
    }
}