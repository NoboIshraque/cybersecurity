package com.mirage.console;

import com.mirage.common.AttackEvent;
import com.mirage.common.JsonCodec;
import com.mirage.common.MessageType;
import com.mirage.common.SensorMessage;

import javax.net.ServerSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Accepts Sensor connections on a thread pool and reads newline-delimited JSON.
 * EVENT messages are enqueued for the correlation engine; HEARTBEATs update the
 * {@link SensorRegistry}. Pass a plain {@link ServerSocketFactory} for local/dev,
 * or an {@code SSLServerSocketFactory} built from the lab keystore for TLS.
 */
public final class ConsoleServer {

    private final ServerSocketFactory socketFactory;
    private final BlockingQueue<AttackEvent> events;
    private final SensorRegistry registry;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    private volatile boolean running;
    private ServerSocket server;

    public ConsoleServer(ServerSocketFactory socketFactory, BlockingQueue<AttackEvent> events,
                         SensorRegistry registry) {
        this.socketFactory = socketFactory;
        this.events = events;
        this.registry = registry;
    }

    /** Binds and starts accepting in the background. Returns the bound port. */
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
                // server closed or transient error; stop if we're shutting down
            }
        }
    }

    private void handle(Socket socket) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isBlank()) {
                    route(JsonCodec.decode(line));
                }
            }
        } catch (IOException e) {
            // Sensor disconnected
        } catch (RuntimeException e) {
            // malformed message; drop this connection
        }
    }

    private void route(SensorMessage message) {
        if (message.type() == MessageType.HEARTBEAT && message.sensorId() != null) {
            registry.markSeen(message.sensorId(), message.timestamp());
        } else if (message.type() == MessageType.EVENT && message.event() != null) {
            events.offer(message.event());
        }
    }

    public void stop() throws IOException {
        running = false;
        if (server != null) {
            server.close();
        }
        pool.shutdownNow();
    }
}
