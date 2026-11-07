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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Accepts Sensor connections on a thread pool and reads newline-delimited JSON.
 * EVENT messages are enqueued for the correlation engine; HEARTBEATs update the
 * {@link SensorRegistry}. The Console can also push {@link ResponseCommand}s back
 * to every connected Sensor via {@link #broadcast}, which is how active defence
 * reaches the Sensors.
 */
public final class ConsoleServer implements CommandSink {

    /** One connected Sensor: its socket plus a writer for pushing commands back. */
    private static final class Connection {
        private final Socket socket;
        private final Writer out;
        volatile String sensorId;

        Connection(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        }

        synchronized void send(String line) {
            try {
                out.write(line);
                out.write('\n');
                out.flush();
            } catch (IOException e) {
                // connection is going away; it will be removed on the read side
            }
        }

        void closeQuietly() {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
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

    private volatile java.net.InetAddress bindAddress;

    /** Listen only on this address ({@code null}: every interface). Call before {@link #start}. */
    public ConsoleServer setBindAddress(java.net.InetAddress address) {
        this.bindAddress = address;
        return this;
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
        }
    }

    private void route(Connection conn, SensorMessage message) {
        if (message.type() == MessageType.HEARTBEAT && message.sensorId() != null) {
            if (conn.sensorId == null) {
                Thread.currentThread().setName("rx-" + message.sensorId());
                trace.line("console", "rx", null, TraceLevel.GOOD,
                        "sensor registered: " + message.sensorId());
            }
            conn.sensorId = message.sensorId();
            registry.markSeen(message.sensorId(),
                    message.timestamp() != null ? message.timestamp() : Instant.now());
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
            conn.send(line);
            sent++;
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
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    private static String abbreviate(String line) {
        return line.length() <= 80 ? line : line.substring(0, 80) + "…";
    }
}
