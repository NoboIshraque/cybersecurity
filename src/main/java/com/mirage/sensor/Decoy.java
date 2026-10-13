package com.mirage.sensor;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Template Method: the accept-observe-interact loop is fixed here; each subclass
 * supplies {@link #interact}. A {@link ConnectionGate} decides whether a source
 * is served normally, tarpitted (held and dripped one byte at a time), or blocked
 * (dropped immediately).
 */
public abstract class Decoy {

    private static final long TARPIT_DRIP_MS = 5000;

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

    public int port() {
        return port;
    }

    public int boundPort() {
        return boundPort;
    }

    public final void start(Consumer<AttackEvent> sink) throws IOException {
        start(sink, ConnectionGate.ALLOW_ALL);
    }

    public final void start(Consumer<AttackEvent> sink, ConnectionGate gate) throws IOException {
        ServerSocket server = new ServerSocket(port); // port 0 binds an ephemeral port
        this.boundPort = server.getLocalPort();
        this.running = true;
        Thread t = new Thread(() -> acceptLoop(server, sink, gate), "decoy-" + type + "-" + boundPort);
        t.setDaemon(true);
        t.start();
    }

    private void acceptLoop(ServerSocket server, Consumer<AttackEvent> sink, ConnectionGate gate) {
        while (running) {
            try (Socket socket = server.accept()) {
                String src = ((InetSocketAddress) socket.getRemoteSocketAddress())
                        .getAddress().getHostAddress();
                int srcPort = socket.getPort();
                ConnectionGate.Decision decision = gate.decide(src);

                if (decision == ConnectionGate.Decision.BLOCK) {
                    sink.accept(new AttackEvent(src, srcPort, type, Action.CONNECT, "blocked", Instant.now()));
                    continue; // socket closed by try-with-resources -> dropped
                }

                sink.accept(new AttackEvent(src, srcPort, type, Action.CONNECT, "", Instant.now()));
                if (decision == ConnectionGate.Decision.TARPIT) {
                    tarpit(socket);
                } else {
                    interact(socket, src, srcPort, sink);
                }
            } catch (IOException e) {
                // socket closed or decoy stopped; keep serving the next client
            }
        }
    }

    /** Hold the connection open, dripping one byte at a time, so the attack stalls. */
    private void tarpit(Socket socket) throws IOException {
        OutputStream out = socket.getOutputStream();
        while (running && !socket.isClosed()) {
            out.write('.');
            out.flush();
            try {
                Thread.sleep(TARPIT_DRIP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void stop() {
        running = false;
    }

    protected AttackEvent event(String sourceIp, int sourcePort, Action action, String payload) {
        return new AttackEvent(sourceIp, sourcePort, type, action, payload, Instant.now());
    }

    protected abstract void interact(Socket socket, String sourceIp, int sourcePort,
                                     Consumer<AttackEvent> sink) throws IOException;
}
