package com.mirage.specter;

import com.mirage.common.trace.TraceLevel;
import com.mirage.common.trace.TraceSink;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A passive listener on the attacker's own laptop. It only accepts and logs:
 * when the Console turns a technique back on this host, each inbound connection
 * is reported to a {@link TraceSink} (channel {@code watch}) so Specter can show
 * an "INCOMING" warning shell. It never sends anything, never scans, and never
 * reaches out — so it adds no attack capability of its own.
 */
public final class CounterWatch {

    private final int port;
    private final TraceSink trace;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final AtomicInteger hits = new AtomicInteger();
    private volatile boolean running;
    private ServerSocket server;

    public CounterWatch(int port, TraceSink trace) {
        this.port = port;
        this.trace = trace == null ? TraceSink.NONE : trace;
    }

    /** Bind the listener and start accepting (returns the bound port; 0 binds an ephemeral one). */
    public int start() throws IOException {
        server = new ServerSocket(port);
        running = true;
        int bound = server.getLocalPort();
        Thread t = new Thread(this::acceptLoop, "counter-watch-" + bound);
        t.setDaemon(true);
        t.start();
        trace.line("watch", "listen", null, TraceLevel.INFO,
                "counter-watch listening on :" + bound + " (accept-and-log only)");
        return bound;
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = server.accept();
                pool.submit(() -> observe(socket));
            } catch (IOException e) {
                return; // stopped
            }
        }
    }

    private void observe(Socket socket) {
        String peer = socket.getInetAddress().getHostAddress();
        int n = hits.incrementAndGet();
        Thread.currentThread().setName("incoming-" + peer + "-" + n);
        trace.line("watch", "incoming", peer, TraceLevel.BAD,
                "INCOMING connection #" + n + " from " + peer + ":" + socket.getPort()
                        + " on :" + socket.getLocalPort());
        if (n == 1) {
            trace.line("watch", "incoming", peer, TraceLevel.BANNER, "INCOMING COUNTER-ATTACK");
        }
        // Read and discard whatever arrives; never write anything back.
        try (Socket s = socket) {
            byte[] buffer = new byte[256];
            int read = s.getInputStream().read(buffer);
            if (read > 0) {
                String preview = new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8)
                        .lines().findFirst().orElse("").trim();
                if (!preview.isEmpty()) {
                    trace.line("watch", "incoming", peer, TraceLevel.WARN, "  payload: " + preview);
                }
            }
        } catch (IOException e) {
            // peer closed; nothing to do
        }
    }

    public int hits() {
        return hits.get();
    }

    public void stop() {
        running = false;
        pool.shutdownNow();
        try {
            if (server != null) {
                server.close();
            }
        } catch (IOException e) {
            // already closed
        }
    }
}
