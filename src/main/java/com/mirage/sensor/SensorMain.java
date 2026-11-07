package com.mirage.sensor;

import com.mirage.common.AttackEvent;
import com.mirage.common.LabConfig;
import com.mirage.common.LinkSecurity;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
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
        try {
            start(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Refusing to start: " + e.getMessage()); // e.g. a remote Console without TLS
            return;
        }
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

        // A-02 / A-03: plain text only to a Console on this machine, otherwise mutual TLS pinned to
        // the lab certificate, so no impostor can give this Sensor orders. Checked before any decoy binds.
        LinkSecurity security = LinkSecurity.fromEnvironment();
        SocketFactory factory = security.clientFactory(consoleHost);
        String origin = "the Console at " + consoleHost + ":" + consolePort
                + (security.isTls() ? ", TLS-verified" : ", this machine");
        System.out.println("Console link: " + security.describe() + ".");

        // Keeps (re)connecting in the background, so the Console may start later or restart (B-05).
        // Created now so the decoys can report into it; it only connects once start() is called.
        SensorLink link = new SensorLink(sensorId, factory, consoleHost, consolePort,
                command -> { // active-defence commands from the Console; the link ACKs each one
                    defence.apply(command);
                    System.out.println("[defence] applied " + command.type() + " " + command.targetIp()
                            + " (from " + origin + ")");
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

        // Reports what changed while the Sensor was down, plants missing lures, then watches (B-29, A-05).
        Path baitDir = config.baitDir();
        BaitFileWatcher bait = new BaitFileWatcher(baitDir, sink);
        try {
            bait.start();
        } catch (IOException e) {
            decoys.forEach(Decoy::stop);
            throw e;
        }
        System.out.println("Watching bait files in " + baitDir.toAbsolutePath()
                + " (subfolders included; SHA-256 baseline in " + baitDir.getFileName() + ".sha256).");

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
}
