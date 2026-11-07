package com.mirage.sensor;

import com.mirage.common.AttackEvent;
import com.mirage.common.LabConfig;
import com.mirage.common.TlsFactories;

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

    public static void main(String[] args) throws Exception {
        System.out.println("Mirage Sensor starting...");

        LabConfig config = LabConfig.load(args);
        // A bare first argument is still accepted as the console host (back-compat with DEMO.md).
        String consoleHost = (args.length > 0 && !args[0].startsWith("--")) ? args[0] : config.consoleHost();
        int consolePort = config.consolePort();
        String sensorId = "sensor-" + InetAddress.getLocalHost().getHostName();
        DefenceState defence = new DefenceState();

        SensorClient client = null;
        try {
            client = new SensorClient(sensorId, socketFactory(), consoleHost, consolePort);
            final SensorClient link = client;
            client.startReceiving(command -> { // active-defence commands from the Console
                defence.apply(command);
                System.out.println("[defence] applied " + command.type() + " " + command.targetIp());
                try {
                    link.sendAck(command); // lets the Console show "applied" in its shell
                } catch (IOException e) {
                    // link down; the command is still applied locally
                }
            });
            System.out.println("Connected to Console at " + consoleHost + ":" + consolePort
                    + " as " + sensorId);
        } catch (IOException e) {
            System.out.println("Could not reach Console at " + consoleHost + ":" + consolePort
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

        new FakeHttpLoginDecoy(config.httpDecoyPort()).start(sink, defence);
        new FakeFtpDecoy(config.ftpDecoyPort()).start(sink, defence);
        new FakeTelnetDecoy(config.telnetDecoyPort()).start(sink, defence);
        System.out.println("Decoys listening: http-login:" + config.httpDecoyPort()
                + ", ftp:" + config.ftpDecoyPort() + ", telnet:" + config.telnetDecoyPort()
                + " (defence-gated).");

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

        System.out.println("Sensor running. Ctrl+C to stop.");
        Thread.currentThread().join();
    }

    /** TLS when a truststore is configured (-Dmirage.truststore=...), otherwise plain sockets. */
    private static SocketFactory socketFactory() {
        String truststore = System.getProperty("mirage.truststore");
        if (truststore != null) {
            char[] password = System.getProperty("mirage.truststore.password", "changeit").toCharArray();
            try {
                SocketFactory factory = TlsFactories.clientFactory(Path.of(truststore), password);
                System.out.println("TLS enabled (truststore " + truststore + ").");
                return factory;
            } catch (RuntimeException e) {
                System.out.println("TLS truststore failed (" + e.getMessage() + "); using plain sockets.");
            }
        }
        return SocketFactory.getDefault();
    }
}
