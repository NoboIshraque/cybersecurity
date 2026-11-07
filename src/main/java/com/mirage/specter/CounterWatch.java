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

    /** How long to keep reading what a prober sends before closing (well under its reply timeout). */
    private static final int DRAIN_MILLIS = 400;
    private static final int MAX_DRAIN = 4096;

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
        // Read and discard whatever arrives; never write anything back. Everything sent is
        // drained before the close, so the close is a quiet one (unread data would make the
        // OS reset the connection, which the prober would read as "blocked").
        try (Socket s = socket) {
            s.setSoTimeout(DRAIN_MILLIS);
            java.io.ByteArrayOutputStream seen = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            try {
                int read;
                while (seen.size() < MAX_DRAIN && (read = s.getInputStream().read(buffer)) > 0) {
                    seen.write(buffer, 0, read);
                }
            } catch (java.net.SocketTimeoutException quiet) {
                // the prober is waiting for a reply that will never come
            }
            String preview = seen.toString(java.nio.charset.StandardCharsets.UTF_8)
                    .lines().findFirst().orElse("").trim();
            if (!preview.isEmpty()) {
                trace.line("watch", "incoming", peer, TraceLevel.WARN, "  payload: " + preview);
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
