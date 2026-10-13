package com.mirage.console.response;

public final class FeedFakeDataAction implements ResponseAction {

    private final String sourceIp;

    public FeedFakeDataAction(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    @Override
    public void execute() {
        // TODO: serve this source a bottomless maze of fake files and honeytokens.
        System.out.println("[response] feeding fake data to " + sourceIp);
    }

    @Override
    public String describe() {
        return "feed-fakes " + sourceIp;
    }
}
