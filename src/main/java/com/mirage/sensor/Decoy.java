package com.mirage.sensor;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Template Method: the accept-observe-interact loop is fixed here; each subclass
 * supplies {@link #interact}, where it reads what the client sends, emits richer
 * events (such as a LOGIN_ATTEMPT with the attempted credentials), and writes a
 * convincing reply. A decoy never grants real access.
 */
public abstract class Decoy {

    protected final String type;
    protected final int port;
    private volatile boolean running;
    private volatile int boundPort = -1;

    protected Decoy(String type, int port) {
        this.type = type;
        this.port = port;
    }

    public String type() {
        return type;
    }

    /** The configured port (may be 0 for "any"). Use {@link #boundPort()} after start. */
    public int port() {
        return port;
    }

    /** The actual port the decoy is listening on, available after {@link #start}. */
    public int boundPort() {
        return boundPort;
    }

    public final void start(Consumer<AttackEvent> sink) throws IOException {
        ServerSocket server = new ServerSocket(port); // port 0 binds an ephemeral port
        this.boundPort = server.getLocalPort();
        this.running = true;
        Thread t = new Thread(() -> acceptLoop(server, sink), "decoy-" + type + "-" + boundPort);
        t.setDaemon(true);
        t.start();
    }

    private void acceptLoop(ServerSocket server, Consumer<AttackEvent> sink) {
        while (running) {
            try (Socket socket = server.accept()) {
                String src = ((InetSocketAddress) socket.getRemoteSocketAddress())
                        .getAddress().getHostAddress();
                int srcPort = socket.getPort();
                sink.accept(new AttackEvent(src, srcPort, type, Action.CONNECT, "", Instant.now()));
                interact(socket, src, srcPort, sink);
            } catch (IOException e) {
                // socket closed or decoy stopped; keep serving the next client
            }
        }
    }

    public void stop() {
        running = false;
    }

    /** Convenience for subclasses to build an event of this decoy's type. */
    protected AttackEvent event(String sourceIp, int sourcePort, Action action, String payload) {
        return new AttackEvent(sourceIp, sourcePort, type, action, payload, Instant.now());
    }

    protected abstract void interact(Socket socket, String sourceIp, int sourcePort,
                                     Consumer<AttackEvent> sink) throws IOException;
}
