package com.mirage.console.response;

/** Command: a single active-defence action, dispatched to a Sensor to run. */
public interface ResponseAction {
    void execute();
    String describe();
}
