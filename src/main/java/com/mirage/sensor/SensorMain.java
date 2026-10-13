package com.mirage.sensor;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Entry point for a Sensor host: runs the decoys and reports to the Console. */
public final class SensorMain {

    private static final int CONSOLE_PORT = 8443;

    public static void main(String[] args) throws Exception {
        System.out.println("Mirage Sensor starting...");

        String consoleHost = args.length > 0 ? args[0] : "127.0.0.1";
        String sensorId = "sensor-" + InetAddress.getLocalHost().getHostName();

        SensorClient client = null;
        try {
            client = new SensorClient(sensorId, SocketFactory.getDefault(), consoleHost, CONSOLE_PORT);
            System.out.println("Connected to Console at " + consoleHost + ":" + CONSOLE_PORT
                    + " as " + sensorId);
        } catch (IOException e) {
            System.out.println("Could not reach Console at " + consoleHost + ":" + CONSOLE_PORT
                    + " (" + e.getMessage() + "). Running decoys locally without reporting.");
        }
        final SensorClient reporter = client;

        Decoy http = new FakeHttpLoginDecoy(8081);
        http.start(event -> {
            System.out.println("[decoy] " + event.sourceIp() + " touched " + event.decoyType());
            if (reporter != null) {
                try {
                    reporter.sendEvent(event);
                } catch (IOException e) {
                    // link down; keep serving the decoy
                }
            }
        });
        System.out.println("Fake HTTP login decoy listening on port " + http.port() + ".");

        if (reporter != null) {
            ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor();
            heartbeats.scheduleAtFixedRate(() -> {
                try {
                    reporter.sendHeartbeat();
                } catch (IOException e) {
                    // link down
                }
            }, 0, 10, TimeUnit.SECONDS);
        }

        System.out.println("TODO (Phase 2): add FTP/Telnet decoys, login parsing, bait-file watcher. "
                + "See HANDOVER.md.");
        Thread.currentThread().join();
    }
}
