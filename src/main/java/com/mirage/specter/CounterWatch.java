package com.mirage.specter;

import com.mirage.common.trace.TraceLevel;
import com.mirage.common.trace.TraceSink;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A passive listener on the attacker's own laptop. It only accepts and logs:
 * when the Console turns a technique back on this host, each inbound connection
 * is reported to a {@link TraceSink} (channel {@code watch}) so Specter can show
 * an "INCOMING" warning shell. It never sends anything, never scans, and never
 * reaches out — so it adds no attack capability of its own.
 *
 * <p>Anyone on the LAN can reach it, so a burst costs bounded resources (A-08): a fixed pool of
 * {@link #WORKERS} threads with a short queue, at most {@link #MAX_PER_SOURCE} connections at
 * once from one address (the rest are closed straight away), and at most
 * {@link #LOG_PER_WINDOW} "INCOMING" lines per source every {@link #LOG_WINDOW_MILLIS} ms.
 * Every connection is still counted in {@link #hits()}.
 */
public final class CounterWatch {

    /** How long to keep reading what a prober sends before closing (well under its reply timeout). */
    private static final int DRAIN_MILLIS = 400;
    private static final int MAX_DRAIN = 4096;

    /** Threads that observe connections. */
    public static final int WORKERS = 16;
    /** Connections waiting for a worker before new ones are closed. */
    static final int QUEUE = 64;
    /** Connections observed at once from one address (the Console's simultaneous counter uses 4). */
    public static final int MAX_PER_SOURCE = 8;
    /** "INCOMING" lines shown per source per log window. */
    public static final int LOG_PER_WINDOW = 10;
    static final long LOG_WINDOW_MILLIS = 5000;

    private final int port;
    private final TraceSink trace;
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(WORKERS, WORKERS, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE), r -> {
                Thread t = new Thread(r, "incoming");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final Map<String, Integer> openPerSource = new ConcurrentHashMap<>();
    /** Per source: [window start ms, lines shown in it, summary said]; guarded by itself. */
    private final Map<String, long[]> logWindows = new HashMap<>();
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
                admit(socket);
            } catch (IOException e) {
                return; // stopped
            }
        }
    }

    /** On the accept thread: count and announce, then observe on a worker if there is room. */
    private void admit(Socket socket) {
        String peer = socket.getInetAddress().getHostAddress();
        int n = hits.incrementAndGet();
        announce(peer, n, socket);
        int open = openPerSource.merge(peer, 1, Integer::sum);
        if (open > MAX_PER_SOURCE) {
            release(peer);
            closeQuietly(socket); // too many at once from this address
            return;
        }
        try {
            pool.execute(() -> {
                try {
                    observe(socket, peer, n);
                } finally {
                    release(peer);
                }
            });
        } catch (RejectedExecutionException e) {
            release(peer);
            closeQuietly(socket); // every worker busy and the queue full
        }
    }

    private void announce(String peer, int n, Socket socket) {
        if (mayLog(peer)) {
            trace.line("watch", "incoming", peer, TraceLevel.BAD,
                    "INCOMING connection #" + n + " from " + peer + ":" + socket.getPort()
                            + " on :" + socket.getLocalPort());
        }
        if (n == 1) {
            trace.line("watch", "incoming", peer, TraceLevel.BANNER, "INCOMING COUNTER-ATTACK");
        }
    }

    /** At most LOG_PER_WINDOW lines per source per window; one summary line when that is reached. */
    private boolean mayLog(String peer) {
        long now = System.currentTimeMillis();
        boolean summarise;
        synchronized (logWindows) {
            long[] w = logWindows.computeIfAbsent(peer, k -> new long[]{now, 0, 0});
            if (now - w[0] >= LOG_WINDOW_MILLIS) {
                w[0] = now;
                w[1] = 0;
                w[2] = 0;
            }
            if (w[1] < LOG_PER_WINDOW) {
                w[1]++;
                return true;
            }
            summarise = w[2] == 0;
            w[2] = 1;
        }
        if (summarise) {
            trace.line("watch", "incoming", peer, TraceLevel.WARN, "… further connections from " + peer
                    + " are counted but not shown for " + LOG_WINDOW_MILLIS / 1000 + " s");
        }
        return false;
    }

    private void release(String peer) {
        openPerSource.computeIfPresent(peer, (k, v) -> v <= 1 ? null : v - 1);
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // already closed
        }
    }

    private void observe(Socket socket, String peer, int n) {
        Thread.currentThread().setName("incoming-" + peer + "-" + n);
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
