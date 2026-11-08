package com.mirage.console.ui;

import java.util.function.Consumer;

/**
 * Runs an operator {@link Operation} against a source and hands back the result
 * line (on the Swing thread). The plain runner does the work inline; the live
 * runner ({@link OperationShells}) opens an operation shell first and does the
 * work on a named worker thread, so the shell can show it happening.
 */
@FunctionalInterface
public interface OperationRunner {

    void run(Operation operation, String sourceIp, Consumer<String> onResult);
}
