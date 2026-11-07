package com.mirage.console;

import com.mirage.common.AttackEvent;
import com.mirage.common.JsonCodec;
import com.mirage.common.MessageType;
import com.mirage.common.ResponseCommand;
import com.mirage.common.SensorMessage;
import com.mirage.common.trace.TraceLevel;
import com.mirage.common.trace.TraceSink;

import javax.net.ServerSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final CopyOnWriteArrayList<Connection> connections = new CopyOnWriteArrayList<>();

    private volatile boolean running;
    private volatile TraceSink trace = TraceSink.NONE;
    private ServerSocket server;

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
        server = socketFactory.createServerSocket(port);
        running = true;
        pool.submit(this::acceptLoop);
        return server.getLocalPort();
    }

    private void acceptLoop() {
        Thread.currentThread().setName("accept");
        while (running) {
            try {
                Socket socket = server.accept();
                pool.submit(() -> handle(socket));
            } catch (IOException e) {
                // server closed or transient error
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
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isBlank()) {
                    route(conn, JsonCodec.decode(line));
                }
            }
        } catch (IOException e) {
            // Sensor disconnected
        } catch (RuntimeException e) {
            // malformed message; drop this connection
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
            registry.markSeen(message.sensorId(), message.timestamp());
        } else if (message.type() == MessageType.EVENT && message.event() != null) {
            AttackEvent event = message.event().withSensor(conn.sensorId);
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

    public void stop() throws IOException {
        running = false;
        if (server != null) {
            server.close();
        }
        pool.shutdownNow();
    }
}
