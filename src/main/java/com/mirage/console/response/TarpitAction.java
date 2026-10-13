package com.mirage.console.response;

public final class TarpitAction implements ResponseAction {

    private final String sourceIp;

    public TarpitAction(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    @Override
    public void execute() {
        // TODO: tell the owning Sensor to hold this source's sockets and drip
        //       one byte at a time, so its scan or brute-force never completes.
        System.out.println("[response] tarpitting " + sourceIp);
    }

    @Override
    public String describe() {
        return "tarpit " + sourceIp;
    }
}
