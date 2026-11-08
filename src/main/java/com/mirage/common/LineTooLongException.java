package com.mirage.common;

import java.io.IOException;

/** A peer sent a line longer than the reader's cap; the connection should be closed. */
public final class LineTooLongException extends IOException {

    private final int limit;

    public LineTooLongException(int limit) {
        super("line longer than " + limit + " bytes");
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }
}
