package com.mirage.common;

/** The kind of interaction a decoy observed. */
public enum Action {
    CONNECT,
    PORT_PROBE,
    LOGIN_ATTEMPT,
    FILE_CHANGE,
    HONEYTOKEN_USE,
    /** The decoy let the intruder "in" (a trap credential worked) — a honeypot session began. */
    DECOY_ACCESS
}
