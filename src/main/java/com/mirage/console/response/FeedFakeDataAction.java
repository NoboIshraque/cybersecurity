package com.mirage.console.response;

import com.mirage.common.CommandType;
import com.mirage.common.ResponseCommand;
import com.mirage.console.CommandSink;

public final class FeedFakeDataAction implements ResponseAction {

    private final CommandSink sink;
    private final String sourceIp;

    public FeedFakeDataAction(CommandSink sink, String sourceIp) {
        this.sink = sink;
        this.sourceIp = sourceIp;
    }

    @Override
    public void execute() {
        sink.broadcast(new ResponseCommand(CommandType.FEED_FAKE, sourceIp));
        System.out.println("[response] feeding fake data to " + sourceIp);
    }

    @Override
    public String describe() {
        return "feed-fakes " + sourceIp;
    }
}
