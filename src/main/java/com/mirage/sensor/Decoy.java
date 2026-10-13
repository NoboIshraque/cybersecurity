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
 * Template Method: the accept-observe-report-respond loop is fixed here; each
 * subclass supplies only {@link #respond}, so a new decoy is a few lines.
 */
public abstract class Decoy {

    protected final String type;
    protected final int port;
    private volatile boolean running;

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

    public final void start(Consumer<AttackEvent> sink) throws IOException {
        running = true;
        ServerSocket server = new ServerSocket(port);
        Thread t = new Thread(() -> acceptLoop(server, sink), "decoy-" + type + "-" + port);
        t.setDaemon(true);
        t.start();
    }

    private void acceptLoop(ServerSocket server, Consumer<AttackEvent> sink) {
        while (running) {
            try {
                Socket socket = server.accept();
                String src = ((InetSocketAddress) socket.getRemoteSocketAddress())
                        .getAddress().getHostAddress();
                AttackEvent event = new AttackEvent(
                        src, socket.getPort(), type, Action.CONNECT, "", Instant.now());
                sink.accept(event);      // report to the Console
                respond(socket, event);  // subclass-specific reply
                socket.close();
            } catch (IOException e) {
                // socket closed or decoy stopped; keep serving the next client
            }
        }
    }

    public void stop() {
        running = false;
    }

    /** Imitate the service here. A decoy logs everything and never grants real access. */
    protected abstract void respond(Socket socket, AttackEvent event) throws IOException;
}
