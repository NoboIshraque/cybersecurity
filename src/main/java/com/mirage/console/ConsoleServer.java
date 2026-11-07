package com.mirage.console;

import com.mirage.common.AttackEvent;
import com.mirage.common.BoundedLineReader;
import com.mirage.common.LineTooLongException;
import com.mirage.common.JsonCodec;
import com.mirage.common.MessageType;
import com.mirage.common.ResponseCommand;
import com.mirage.common.SensorMessage;
import com.mirage.common.trace.TraceLevel;
import com.mirage.common.trace.TraceSink;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Accepts Sensor connections on a thread pool and reads newline-delimited JSON.
 * EVENT messages are enqueued for the correlation engine; HEARTBEATs update the
 * {@link SensorRegistry}. The Console can also push {@link ResponseCommand}s back
 * to every connected Sensor via {@link #broadcast}, which is how active defence
 * reaches the Sensors.
 */
public final class ConsoleServer implements CommandSink {

    /** Lines a Sensor may have waiting to be sent before it counts as too slow and is dropped. */
    static final int OUTBOX = 256;
    private static final String CLOSE = new String("close"); // identity-compared wake-up for the writer

    /**
     * One connected Sensor: its socket, a bounded outbox and its own writer thread (B-15). Queuing
     * never blocks the caller (the engine or an operator's button); a Sensor that stops reading
     * fills its outbox, or stalls a write past the timeout, and is dropped on its own.
     */
    private static final class Connection {
        private final Socket socket;
        private final Writer out;
        private final BlockingQueue<String> outbox = new ArrayBlockingQueue<>(OUTBOX);
        private volatile long writingSince; // System.nanoTime() of the write in progress, 0 when idle
        private volatile boolean closed;
        volatile String sensorId;

        Connection(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        }

        void startWriter(String name) {
            Thread t = new Thread(this::writeLoop, name);
            t.setDaemon(true);
            t.start();
        }

        /** Queue a line; false (and the link is closed) if this Sensor is already too far behind. */
        boolean offer(String line) {
            if (closed) {
                return false;
            }
            if (!outbox.offer(line)) {
                closeQuietly();
                return false;
            }
            return true;
        }

        /** True if one write has been blocked for longer than {@code timeoutNanos}. */
        boolean stalled(long now, long timeoutNanos) {
            long since = writingSince;
            return since != 0 && now - since > timeoutNanos;
        }

        private void writeLoop() {
            try {
                while (!closed) {
                    String line = outbox.take();
                    if (line == CLOSE) {
                        return;
                    }
                    writingSince = System.nanoTime();
                    out.write(line);
                    out.write('\n');
                    out.flush();
                    writingSince = 0;
                }
            } catch (IOException | InterruptedException e) {
                // link gone: it is removed on the read side
            } finally {
                closeQuietly();
            }
        }

        void closeQuietly() {
            closed = true;
            try {
                socket.close(); // also unblocks a write stuck on a peer that stopped reading
            } catch (IOException e) {
                // ignore
            }
            outbox.clear();
            outbox.offer(CLOSE);
        }
    }

    private final ServerSocketFactory socketFactory;
    private final BlockingQueue<AttackEvent> events;
    private final SensorRegistry registry;
    private ExecutorService pool;
    private final CopyOnWriteArrayList<Connection> connections = new CopyOnWriteArrayList<>();

    private volatile boolean running;
    private volatile TraceSink trace = TraceSink.NONE;
    private ServerSocket server;

    /** Longest line accepted from a Sensor; past it the link is closed (B-08, A-01). */
    public static final int MAX_LINE_BYTES = JsonCodec.MAX_LINE_BYTES;
    /** Sensor links served at once; more are closed on accept. */
    public static final int DEFAULT_MAX_CONNECTIONS = 32;
    /** A link that sends nothing for this long is closed. Sensors heartbeat every 10 s. */
    public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofSeconds(45);

    private volatile int maxConnections = DEFAULT_MAX_CONNECTIONS;
    private volatile Duration idleTimeout = DEFAULT_IDLE_TIMEOUT;
    private final AtomicInteger open = new AtomicInteger();

    /** Override the connection cap. Call before {@link #start}. */
    public ConsoleServer setMaxConnections(int max) {
        if (max < 1) {
            throw new IllegalArgumentException("max connections must be at least 1");
        }
        this.maxConnections = max;
        return this;
    }

    private volatile Persistence persistence = Persistence.NONE;

    /** Record Sensors in the database as they heartbeat and disconnect (B-30). */
    public ConsoleServer setPersistence(Persistence persistence) {
        this.persistence = persistence == null ? Persistence.NONE : persistence;
        return this;
    }

    /** A database problem must never cost a Sensor its link (B-01). */
    private void record(String what, Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            trace.line("console", "rx", null, TraceLevel.WARN, "could not record " + what + ": " + e.getMessage());
        }
    }

    private volatile java.net.InetAddress bindAddress;

    /** Listen only on this address ({@code null}: every interface). Call before {@link #start}. */
    public ConsoleServer setBindAddress(java.net.InetAddress address) {
        this.bindAddress = address;
        return this;
    }

    /** A single write to a Sensor blocked for this long closes its link (B-15). */
    public static final Duration DEFAULT_WRITE_TIMEOUT = Duration.ofSeconds(10);
    private volatile Duration writeTimeout = DEFAULT_WRITE_TIMEOUT;
    private ScheduledExecutorService watchdog;

    /** Override the write timeout. Call before {@link #start}. */
    public ConsoleServer setWriteTimeout(Duration timeout) {
        this.writeTimeout = Objects.requireNonNull(timeout);
        return this;
    }

    /** Close any link whose current write has been stuck past the timeout. */
    private void dropStalledLinks() {
        long now = System.nanoTime();
        long limit = writeTimeout.toNanos();
        for (Connection conn : connections) {
            if (conn.stalled(now, limit)) {
                trace.line("console", "dispatch", null, TraceLevel.WARN, "closing the link to "
                        + describe(conn) + ": it stopped reading commands (a write blocked for over "
                        + writeTimeout.toMillis() + " ms)");
                conn.closeQuietly();
            }
        }
    }

    private static String describe(Connection conn) {
        return conn.sensorId != null ? conn.sensorId : conn.socket.getInetAddress().getHostAddress();
    }

    /** Override the idle timeout. Call before {@link #start}. */
    public ConsoleServer setIdleTimeout(Duration timeout) {
        this.idleTimeout = Objects.requireNonNull(timeout);
        return this;
    }

    /** Send a trace line for every decoded message and broadcast (default: none). */
    public void setTrace(TraceSink trace) {
        this.trace = trace == null ? TraceSink.NONE : trace;
    }

    public ConsoleServer(ServerSocketFactory socketFactory, BlockingQueue<AttackEvent> events,
                         SensorRegistry registry) {
        this.socketFactory = socketFactory;
        this.events = events;
        this.registry = registry;
    }

    public int start(int port) throws IOException {
        server = bindAddress == null ? socketFactory.createServerSocket(port)
                : socketFactory.createServerSocket(port, 50, bindAddress); // A-02: loopback unless TLS
        // One rx thread per link, never more than the cap (A-01: was an unbounded cached pool).
        pool = Executors.newFixedThreadPool(maxConnections, r -> {
            Thread t = new Thread(r, "rx");
            t.setDaemon(true);
            return t;
        });
        running = true;
        Thread accept = new Thread(this::acceptLoop, "accept");
        accept.setDaemon(true);
        accept.start();
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tx-watchdog");
            t.setDaemon(true);
            return t;
        });
        long every = Math.max(50, Math.min(1000, writeTimeout.toMillis() / 4));
        watchdog.scheduleWithFixedDelay(this::dropStalledLinks, every, every, TimeUnit.MILLISECONDS);
        return server.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            Socket socket;
            try {
                socket = server.accept();
            } catch (IOException e) {
                if (server.isClosed()) {
                    return;
                }
                continue; // transient error
            }
            if (open.incrementAndGet() > maxConnections) {
                open.decrementAndGet();
                trace.line("console", "rx", null, TraceLevel.WARN, "refused a link from "
                        + socket.getInetAddress().getHostAddress() + ": " + maxConnections + " links already open");
                try {
                    socket.close();
                } catch (IOException e) {
                    // ignore
                }
                continue;
            }
            try {
                pool.execute(() -> {
                    try {
                        handle(socket);
                    } finally {
                        open.decrementAndGet();
                    }
                });
            } catch (RuntimeException e) { // pool shut down: stopping
                open.decrementAndGet();
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }

    private void handle(Socket socket) {
        Connection conn;
        try {
            conn = new Connection(socket);
        } catch (IOException e) {
            return;
        }
        connections.add(conn);
        conn.startWriter("tx-" + socket.getInetAddress().getHostAddress());
        // Name the pooled thread after its peer so the shells can show which rx thread did the work.
        String peer = socket.getInetAddress().getHostAddress();
        Thread.currentThread().setName("rx-" + peer);
        trace.line("console", "rx", null, TraceLevel.INFO, "sensor link opened from " + peer);
        try {
            socket.setSoTimeout((int) Math.min(Integer.MAX_VALUE, idleTimeout.toMillis()));
            BoundedLineReader in = new BoundedLineReader(socket.getInputStream(), MAX_LINE_BYTES);
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    route(conn, JsonCodec.decode(line));
                } catch (RuntimeException e) {
                    // One bad line is skipped; it must not cost the Sensor its link (B-13).
                    trace.line("console", "rx", null, TraceLevel.WARN, "skipped an unreadable line from "
                            + peer + ": " + abbreviate(line));
                }
            }
        } catch (LineTooLongException e) {
            trace.line("console", "rx", null, TraceLevel.BAD, "closing the link from " + peer + ": "
                    + e.getMessage() + " (no newline)");
        } catch (SocketTimeoutException e) {
            trace.line("console", "rx", null, TraceLevel.WARN, "closing the link from " + peer + ": silent for "
                    + idleTimeout.toSeconds() + " s (no heartbeat)");
        } catch (IOException e) {
            // Sensor disconnected
        } finally {
            connections.remove(conn);
            conn.closeQuietly();
            trace.line("console", "rx", null, TraceLevel.WARN, "sensor link closed (" + peer + ")");
            if (conn.sensorId != null) {
                String gone = conn.sensorId;
                record("sensor " + gone + " offline", () -> persistence.sensorOffline(gone));
            }
        }
    }

    /** A Sensor clock this far off this Console's is worth a warning. */
    static final Duration SKEW_WARNING = Duration.ofSeconds(10);

    private void warnIfSkewed(String sensorId, Instant sensorTime, Instant received) {
        if (sensorTime == null) {
            return;
        }
        Duration skew = Duration.between(received, sensorTime);
        if (skew.abs().compareTo(SKEW_WARNING) > 0) {
            trace.line("console", "rx", null, TraceLevel.WARN, "sensor " + sensorId + "'s clock is "
                    + skew.abs().toSeconds() + " s " + (skew.isNegative() ? "slow" : "fast")
                    + ": the event times it reports are off by that much (liveness uses this Console's clock)");
        }
    }

    private void route(Connection conn, SensorMessage message) {
        if (message.type() == MessageType.HEARTBEAT && message.sensorId() != null) {
            // B-14: liveness runs on this Console's clock. The Sensor's own timestamp is only
            // compared once, to point out a skewed laptop clock.
            Instant received = Instant.now();
            if (conn.sensorId == null) {
                Thread.currentThread().setName("rx-" + message.sensorId());
                trace.line("console", "rx", null, TraceLevel.GOOD,
                        "sensor registered: " + message.sensorId());
                warnIfSkewed(message.sensorId(), message.timestamp(), received);
            }
            conn.sensorId = message.sensorId();
            registry.markSeen(message.sensorId(), received);
            String sensorId = message.sensorId();
            String ip = conn.socket.getInetAddress().getHostAddress();
            record("sensor " + sensorId, () -> persistence.sensorSeen(sensorId, ip, received));
        } else if (message.type() == MessageType.EVENT && message.event() != null) {
            Optional<String> problem = EventValidator.problem(message.event());
            if (problem.isPresent()) {
                trace.line("console", "rx", null, TraceLevel.BAD,
                        "EVENT refused (" + problem.get() + ") from "
                                + (conn.sensorId != null ? conn.sensorId
                                        : conn.socket.getInetAddress().getHostAddress()));
                return;
            }
            AttackEvent event = EventValidator.normalise(message.event()).withSensor(conn.sensorId);
            trace.line("console", "rx", event.sourceIp(), TraceLevel.INFO,
                    "EVENT " + event.sourceIp() + " -> " + event.decoyType() + " " + event.action()
                            + (event.payload() == null || event.payload().isEmpty() ? "" : "  [" + event.payload() + "]")
                            + "  => queue");
            events.offer(event);
        } else if (message.type() == MessageType.ACK) {
            String note = message.note() == null ? "" : message.note();
            String target = note.contains(" ") ? note.substring(note.indexOf(' ') + 1) : null;
            trace.line("console", "ack", target, TraceLevel.GOOD,
                    message.sensorId() + " confirms: " + note + " applied");
        }
    }

    @Override
    public void broadcast(ResponseCommand command) {
        String line = JsonCodec.encode(command);
        int sent = 0;
        for (Connection conn : connections) {
            if (conn.offer(line)) { // never blocks: each Sensor has its own outbox and writer (B-15)
                sent++;
            } else {
                trace.line("console", "dispatch", command.targetIp(), TraceLevel.WARN, "dropped the link to "
                        + describe(conn) + ": " + OUTBOX + " commands waiting, it stopped reading");
            }
        }
        trace.line("console", "dispatch", command.targetIp(), TraceLevel.INFO,
                "broadcast " + command.type() + " " + command.targetIp() + " to " + sent + " sensor(s)");
    }

    public int connectionCount() {
        return connections.size();
    }

    /** Stop accepting and close every Sensor link, so Sensors see the Console go away and reconnect. */
    public void stop() throws IOException {
        running = false;
        if (server != null) {
            server.close();
        }
        for (Connection conn : connections) {
            conn.closeQuietly();
        }
        if (watchdog != null) {
            watchdog.shutdownNow();
        }
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    private static String abbreviate(String line) {
        return line.length() <= 80 ? line : line.substring(0, 80) + "…";
    }
}
