package com.mirage.console;

import com.mirage.common.AttackEvent;
import com.mirage.common.JsonCodec;
import com.mirage.common.MessageType;
import com.mirage.common.ResponseCommand;
import com.mirage.common.SensorMessage;

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
    private ServerSocket server;

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
        }
    }

    private void route(Connection conn, SensorMessage message) {
        if (message.type() == MessageType.HEARTBEAT && message.sensorId() != null) {
            conn.sensorId = message.sensorId();
            registry.markSeen(message.sensorId(), message.timestamp());
        } else if (message.type() == MessageType.EVENT && message.event() != null) {
            events.offer(message.event().withSensor(conn.sensorId));
        }
    }

    @Override
    public void broadcast(ResponseCommand command) {
        String line = JsonCodec.encode(command);
        for (Connection conn : connections) {
            conn.send(line);
        }
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
