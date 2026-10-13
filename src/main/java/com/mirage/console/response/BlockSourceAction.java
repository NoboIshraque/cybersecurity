package com.mirage.console.response;

public final class BlockSourceAction implements ResponseAction {

    private final String sourceIp;

    public BlockSourceAction(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    @Override
    public void execute() {
        // TODO: push a network-wide block rule for this source to every Sensor.
        System.out.println("[response] blocking " + sourceIp + " network-wide");
    }

    @Override
    public String describe() {
        return "block " + sourceIp;
    }
}
