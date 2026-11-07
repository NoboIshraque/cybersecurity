package com.mirage.sensor;

import com.mirage.common.AttackEvent;
import com.mirage.common.LabConfig;
import com.mirage.common.TlsFactories;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Entry point for a Sensor host: runs the decoys and bait files, reports to the Console. */
public final class SensorMain {

    public static void main(String[] args) throws Exception {
        start(args);
        System.out.println("Sensor running. Ctrl+C to stop.");
        Thread.currentThread().join();
    }

    /**
     * Start the Sensor and return a handle that stops it. Every decoy is bound <b>before</b> the
     * Sensor connects to the Console (B-31): if a decoy port is taken, the decoys already bound
     * are released and a {@link BindException} naming the port is thrown, with no link opened,
     * so the Console never counts a "connected" Sensor that has no decoys.
     */
    public static AutoCloseable start(String[] args) throws Exception {
        System.out.println("Mirage Sensor starting...");

        LabConfig config = LabConfig.load(args);
        // A bare first argument is still accepted as the console host (back-compat with DEMO.md).
        String consoleHost = (args.length > 0 && !args[0].startsWith("--")) ? args[0] : config.consoleHost();
        int consolePort = config.consolePort();
        String sensorId = "sensor-" + InetAddress.getLocalHost().getHostName();
        DefenceState defence = new DefenceState();

        // Keeps (re)connecting in the background, so the Console may start later or restart (B-05).
        // Created now so the decoys can report into it; it only connects once start() is called.
        SensorLink link = new SensorLink(sensorId, socketFactory(), consoleHost, consolePort,
                command -> { // active-defence commands from the Console; the link ACKs each one
                    defence.apply(command);
                    System.out.println("[defence] applied " + command.type() + " " + command.targetIp());
                });

        Consumer<AttackEvent> sink = event -> {
            System.out.println("[decoy] " + event.sourceIp() + " -> " + event.decoyType()
                    + " (" + event.action() + ") " + event.payload());
            link.sendEvent(event); // buffered while the link is down
        };

        List<Decoy> decoys = startAll(List.of(
                new FakeHttpLoginDecoy(config.httpDecoyPort()),
                new FakeFtpDecoy(config.ftpDecoyPort()),
                new FakeTelnetDecoy(config.telnetDecoyPort())), sink, defence);
        System.out.println("Decoys listening: http-login:" + config.httpDecoyPort()
                + ", ftp:" + config.ftpDecoyPort() + ", telnet:" + config.telnetDecoyPort()
                + " (defence-gated).");

        Path baitDir = Path.of("bait");
        BaitFileWatcher bait = new BaitFileWatcher(baitDir, sink);
        try {
            Files.createDirectories(baitDir);
            bait.start();
        } catch (IOException e) {
            decoys.forEach(Decoy::stop);
            throw e;
        }
        System.out.println("Watching bait files in " + baitDir.toAbsolutePath() + ".");

        System.out.println("Reporting to Console at " + consoleHost + ":" + consolePort + " as " + sensorId
                + " (reconnects automatically).");
        link.start();

        // Always scheduled: a heartbeat on a down link is a no-op, and the link heartbeats itself on connect.
        ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sensor-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeats.scheduleAtFixedRate(link::sendHeartbeat, 10, 10, TimeUnit.SECONDS);
        return () -> {
            heartbeats.shutdownNow();
            link.close();
            bait.stop();
            decoys.forEach(Decoy::stop);
        };
    }

    /** Bind every decoy, or none: on a failure, release the ones already bound and say which port. */
    private static List<Decoy> startAll(List<Decoy> decoys, Consumer<AttackEvent> sink, ConnectionGate gate)
            throws IOException {
        List<Decoy> started = new ArrayList<>();
        for (Decoy decoy : decoys) {
            try {
                decoy.start(sink, gate);
            } catch (IOException e) {
                started.forEach(Decoy::stop);
                String message = "the " + decoy.type() + " decoy can't listen on port " + decoy.port()
                        + " (" + e.getMessage() + "): is another Sensor or defender already running?";
                IOException failure = e instanceof BindException ? new BindException(message)
                        : new IOException(message);
                failure.initCause(e);
                throw failure;
            }
            started.add(decoy);
        }
        return List.copyOf(started);
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
