package com.mirage.sensor;

/** Entry point for a Sensor host: starts the decoys and reports to the Console. */
public final class SensorMain {

    public static void main(String[] args) throws Exception {
        System.out.println("Mirage Sensor starting...");

        Decoy http = new FakeHttpLoginDecoy(8081);
        http.start(event -> System.out.println(
                "[decoy] " + event.sourceIp() + " touched " + event.decoyType()));

        System.out.println("Fake HTTP login decoy listening on port " + http.port() + ".");
        System.out.println("TODO: add FTP/Telnet decoys, bait-file watcher, TLS link to the "
                + "Console, and heartbeats. See HANDOVER.md.");

        Thread.currentThread().join(); // keep the decoy alive
    }
}
