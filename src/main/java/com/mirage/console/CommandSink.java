package com.mirage.console;

import com.mirage.common.ResponseCommand;

/** Where active-defence commands are sent (the Console broadcasts to its Sensors). */
public interface CommandSink {

    void broadcast(ResponseCommand command);

    CommandSink NONE = command -> { };

    /**
     * Decorator (B-30): send through {@code target}, then record the command in the
     * {@code block_rule} table. A database failure is logged and never stops the command.
     */
    static CommandSink recorded(CommandSink target, Persistence persistence) {
        return command -> {
            target.broadcast(command);
            try {
                persistence.commandSent(command);
            } catch (RuntimeException e) {
                System.out.println("[warn] could not record " + command.type() + " " + command.targetIp()
                        + " in the database: " + e.getMessage());
            }
        };
    }
}
