package com.mirage.common;

/** The kind of message a Sensor sends to the Console. */
public enum MessageType {
    EVENT,
    HEARTBEAT,
    /** The Sensor confirms it applied a response command (the command is in the note). */
    ACK
}
