package com.mirage.console.response;

import com.mirage.common.CommandType;
import com.mirage.common.ResponseCommand;
import com.mirage.console.CommandSink;

public final class TarpitAction implements ResponseAction {

    private final CommandSink sink;
    private final String sourceIp;

    public TarpitAction(CommandSink sink, String sourceIp) {
        this.sink = sink;
        this.sourceIp = sourceIp;
    }

    @Override
    public void execute() {
        sink.broadcast(new ResponseCommand(CommandType.TARPIT, sourceIp));
        System.out.println("[response] tarpitting " + sourceIp);
    }

    @Override
    public String describe() {
        return "tarpit " + sourceIp;
    }
}
