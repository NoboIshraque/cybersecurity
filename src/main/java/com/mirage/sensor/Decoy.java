package com.mirage.sensor;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Template Method: the accept-observe-interact loop is fixed here; each subclass
 * supplies {@link #interact}. A {@link ConnectionGate} decides whether a source
 * is served normally, tarpitted (held and dripped one byte at a time), or blocked
 * (dropped immediately).
 *
 * <p>Each accepted connection is served on a small bounded pool, so a tarpitted
 * client cannot stall the others and several simultaneous probes really run in
 * parallel. The accept thread only accepts; it never does per-connection work.
 */
public abstract class Decoy {

    private static final long TARPIT_DRIP_MS = 5000;
    private static final int WORKERS = 12;

    protected final String type;
    protected final int port;
    private volatile boolean running;
    private volatile int boundPort = -1;
    private final ExecutorService workers = Executors.newFixedThreadPool(WORKERS);
    private ServerSocket server;

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
        this.server = new ServerSocket(port); // port 0 binds an ephemeral port
        this.boundPort = server.getLocalPort();
        this.running = true;
        Thread t = new Thread(() -> acceptLoop(sink, gate), "decoy-" + type + "-" + boundPort);
        t.setDaemon(true);
        t.start();
    }

    private void acceptLoop(Consumer<AttackEvent> sink, ConnectionGate gate) {
        while (running) {
            try {
                Socket socket = server.accept();
                workers.submit(() -> handle(socket, sink, gate)); // each connection on its own thread
            } catch (IOException e) {
                // server closed or decoy stopped; stop accepting
                return;
            }
        }
    }

    private void handle(Socket socket, Consumer<AttackEvent> sink, ConnectionGate gate) {
        Thread.currentThread().setName("decoy-" + type + "-conn");
        try (Socket s = socket) {
            String src = ((InetSocketAddress) s.getRemoteSocketAddress()).getAddress().getHostAddress();
            int srcPort = s.getPort();
            ConnectionGate.Decision decision = gate.decide(src);

            if (decision == ConnectionGate.Decision.BLOCK) {
                sink.accept(new AttackEvent(src, srcPort, type, Action.CONNECT, "blocked", Instant.now()));
                // Reject like a firewall: close with a TCP reset (not a quiet close), so the
                // client can tell "blocked" apart from a service that simply never answers.
                s.setSoLinger(true, 0);
                return; // closed by try-with-resources -> reset
            }

            sink.accept(new AttackEvent(src, srcPort, type, Action.CONNECT, "", Instant.now()));
            if (decision == ConnectionGate.Decision.TARPIT) {
                tarpit(s);
            } else {
                interact(s, src, srcPort, sink);
            }
        } catch (IOException e) {
            // socket closed or decoy stopped; this connection is done
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
        workers.shutdownNow();
        try {
            if (server != null) {
                server.close(); // unblock accept()
            }
        } catch (IOException e) {
            // already closed
        }
    }

    protected AttackEvent event(String sourceIp, int sourcePort, Action action, String payload) {
        return new AttackEvent(sourceIp, sourcePort, type, action, payload, Instant.now());
    }

    protected abstract void interact(Socket socket, String sourceIp, int sourcePort,
                                     Consumer<AttackEvent> sink) throws IOException;
}
