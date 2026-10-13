package com.mirage.common;

/** A scoped active-defence instruction from the Console to the Sensors. */
public record ResponseCommand(CommandType type, String targetIp) {
}
