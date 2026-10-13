package com.mirage.console;

import com.mirage.common.ResponseCommand;

/** Where active-defence commands are sent (the Console broadcasts to its Sensors). */
public interface CommandSink {

    void broadcast(ResponseCommand command);

    CommandSink NONE = command -> { };
}
