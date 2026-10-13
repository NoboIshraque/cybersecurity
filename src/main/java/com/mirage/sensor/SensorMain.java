package com.mirage.sensor;

import com.mirage.common.AttackEvent;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Entry point for a Sensor host: runs the decoys and bait files, reports to the Console. */
public final class SensorMain {

    private static final int CONSOLE_PORT = 8443;

    public static void main(String[] args) throws Exception {
        System.out.println("Mirage Sensor starting...");

        String consoleHost = args.length > 0 ? args[0] : "127.0.0.1";
        String sensorId = "sensor-" + InetAddress.getLocalHost().getHostName();
        DefenceState defence = new DefenceState();

        SensorClient client = null;
        try {
            client = new SensorClient(sensorId, SocketFactory.getDefault(), consoleHost, CONSOLE_PORT);
            client.startReceiving(defence::apply); // active-defence commands from the Console
            System.out.println("Connected to Console at " + consoleHost + ":" + CONSOLE_PORT
                    + " as " + sensorId);
        } catch (IOException e) {
            System.out.println("Could not reach Console at " + consoleHost + ":" + CONSOLE_PORT
                    + " (" + e.getMessage() + "). Running decoys locally without reporting.");
        }
        final SensorClient reporter = client;

        Consumer<AttackEvent> sink = event -> {
            System.out.println("[decoy] " + event.sourceIp() + " -> " + event.decoyType()
                    + " (" + event.action() + ") " + event.payload());
            if (reporter != null) {
                try {
                    reporter.sendEvent(event);
                } catch (IOException e) {
                    // link down; keep serving
                }
            }
        };

        new FakeHttpLoginDecoy(8081).start(sink, defence);
        new FakeFtpDecoy(8021).start(sink, defence);
        new FakeTelnetDecoy(8023).start(sink, defence);
        System.out.println("Decoys listening: http-login:8081, ftp:8021, telnet:8023 (defence-gated).");

        Path baitDir = Path.of("bait");
        Files.createDirectories(baitDir);
        new BaitFileWatcher(baitDir, sink).start();
        System.out.println("Watching bait files in " + baitDir.toAbsolutePath() + ".");

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

        System.out.println("TODO (Phase 5): the Specter red-team console. See HANDOVER.md.");
        Thread.currentThread().join();
    }
}
