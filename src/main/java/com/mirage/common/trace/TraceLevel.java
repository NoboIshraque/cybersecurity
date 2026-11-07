package com.mirage.common.trace;

/** How a trace line should look in a shell: plain, success, warning, failure, a banner, or a typed command. */
public enum TraceLevel {
    INFO,
    GOOD,
    WARN,
    BAD,
    BANNER,
    COMMAND
}
