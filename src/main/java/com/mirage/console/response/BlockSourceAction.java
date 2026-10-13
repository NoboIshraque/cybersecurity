package com.mirage.console.response;

import com.mirage.common.CommandType;
import com.mirage.common.ResponseCommand;
import com.mirage.console.CommandSink;

public final class BlockSourceAction implements ResponseAction {

    private final CommandSink sink;
    private final String sourceIp;

    public BlockSourceAction(CommandSink sink, String sourceIp) {
        this.sink = sink;
        this.sourceIp = sourceIp;
    }

    @Override
    public void execute() {
        sink.broadcast(new ResponseCommand(CommandType.BLOCK, sourceIp));
        System.out.println("[response] blocking " + sourceIp + " network-wide");
    }

    @Override
    public String describe() {
        return "block " + sourceIp;
    }
}
