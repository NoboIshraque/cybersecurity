package com.mirage.sensor;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.BoundedLineReader;
import com.mirage.common.LineTooLongException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Template Method: the accept-observe-interact loop is fixed here; each subclass
 * supplies {@link #interact}. A {@link ConnectionGate} decides whether a source
 * is served normally, tarpitted (held and dripped one byte at a time), or blocked
 * (dropped immediately).
 *
 * <p>No client can hold the decoy hostage (B-06, B-07, A-04), within {@link DecoyLimits}:
 * <ul>
 *   <li>normal interactions run on a small bounded pool with a read timeout, so an idle
 *       client is hung up on instead of holding a worker forever;</li>
 *   <li>one source may only hold a few connections at once, and a full queue refuses new
 *       connections instead of queueing them forever;</li>
 *   <li>tarpits hold no thread: one scheduler drips to every tarpitted socket, there is a
 *       cap on how many are held, and each is closed after a maximum hold time.</li>
 * </ul>
 * A connection refused by a limit is closed with a reset and still reported, so the attempt is
 * seen. The accept thread only classifies and hands off; it never reads from a client.
 */
public abstract class Decoy {

    /** At most one "refused by a limit" event per source in this window, so a flood can't flood the Console. */
    private static final long LIMIT_REPORT_EVERY_MS = 5000;

    /** Longest line a decoy reads from a client. */
    public static final int MAX_LINE_BYTES = 8192;

    protected final String type;
    protected final int port;
    private volatile boolean running;
    private volatile int boundPort = -1;
    private DecoyLimits limits = DecoyLimits.DEFAULT;
    private ThreadPoolExecutor workers;
    private ScheduledExecutorService tarpitDrips;
    private ServerSocket server;
    /** Open connections per source (served + tarpitted). */
    private final Map<String, Integer> perSource = new ConcurrentHashMap<>();
    private final Map<String, Long> lastLimitReport = new ConcurrentHashMap<>();
    private final Set<Socket> tarpitted = ConcurrentHashMap.newKeySet();

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

    /** Use these limits instead of {@link DecoyLimits#DEFAULT}. Call before {@code start}. */
    public final synchronized Decoy limits(DecoyLimits limits) {
        if (running) {
            throw new IllegalStateException("set limits before starting the decoy");
        }
        this.limits = Objects.requireNonNull(limits);
        return this;
    }

    public final void start(Consumer<AttackEvent> sink) throws IOException {
        start(sink, ConnectionGate.ALLOW_ALL);
    }

    public final synchronized void start(Consumer<AttackEvent> sink, ConnectionGate gate) throws IOException {
        this.server = new ServerSocket(port); // port 0 binds an ephemeral port
        this.boundPort = server.getLocalPort();
        this.workers = new ThreadPoolExecutor(limits.workers(), limits.workers(), 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(limits.queue()), daemon("decoy-" + type + "-conn"),
                new ThreadPoolExecutor.AbortPolicy());
        this.tarpitDrips = Executors.newSingleThreadScheduledExecutor(daemon("decoy-" + type + "-tarpit"));
        this.running = true;
        Thread t = new Thread(() -> acceptLoop(sink, gate), "decoy-" + type + "-" + boundPort);
        t.setDaemon(true);
        t.start();
    }

    private void acceptLoop(Consumer<AttackEvent> sink, ConnectionGate gate) {
        while (running) {
            Socket socket;
            try {
                socket = server.accept();
            } catch (IOException e) {
                // server closed or decoy stopped; stop accepting
                return;
            }
            try {
                dispatch(socket, sink, gate);
            } catch (RuntimeException e) {
                closeWithReset(socket); // never let one connection end the accept loop
            }
        }
    }

    /** Runs on the accept thread: classify, apply the limits, hand off. No reads from the client. */
    private void dispatch(Socket socket, Consumer<AttackEvent> sink, ConnectionGate gate) {
        String src = ((InetSocketAddress) socket.getRemoteSocketAddress()).getAddress().getHostAddress();
        int srcPort = socket.getPort();
        ConnectionGate.Decision decision = gate.decide(src);

        if (decision == ConnectionGate.Decision.BLOCK) {
            // Handled on a worker, as before: resetting straight from the accept thread lands the
            // RST before the client's first write more often, which widens the B-24 race.
            Runnable reject = () -> {
                sink.accept(new AttackEvent(src, srcPort, type, Action.CONNECT, "blocked", Instant.now()));
                // Reject like a firewall: close with a TCP reset (not a quiet close), so the
                // client can tell "blocked" apart from a service that simply never answers.
                closeWithReset(socket);
            };
            try {
                workers.execute(reject);
            } catch (RejectedExecutionException e) {
                closeWithReset(socket); // busy: still a reset, just not reported
            }
            return;
        }
        if (!acquire(src)) {
            refuse(socket, src, srcPort, sink,
                    "refused: connection limit (" + limits.maxPerSource() + " per source)");
            return;
        }
        if (decision == ConnectionGate.Decision.TARPIT) {
            if (tarpitted.size() >= limits.maxTarpitted()) {
                release(src);
                refuse(socket, src, srcPort, sink, "refused: tarpit full (" + limits.maxTarpitted() + ")");
                return;
            }
            sink.accept(new AttackEvent(src, srcPort, type, Action.CONNECT, "", Instant.now()));
            tarpit(socket, src);
            return;
        }
        // Reported before it waits for a worker, so even a queued connection is seen.
        sink.accept(new AttackEvent(src, srcPort, type, Action.CONNECT, "", Instant.now()));
        try {
            workers.execute(() -> serve(socket, src, srcPort, sink));
        } catch (RejectedExecutionException e) {
            release(src);
            refuse(socket, src, srcPort, sink, "refused: decoy busy");
        }
    }

    private void serve(Socket socket, String src, int srcPort, Consumer<AttackEvent> sink) {
        try (Socket s = socket) {
            s.setSoTimeout((int) Math.min(Integer.MAX_VALUE, limits.readTimeout().toMillis()));
            interact(s, src, srcPort, sink);
        } catch (LineTooLongException e) {
            // B-08: cut off instead of buffering; worth reporting, it's not a normal client
            sink.accept(new AttackEvent(src, srcPort, type, Action.CONNECT,
                    "refused: " + e.getMessage(), Instant.now()));
        } catch (IOException e) {
            // read timeout, client gone or decoy stopped; this connection is done
        } finally {
            release(src);
        }
    }

    /**
     * Hold the connection open, dripping one byte at a time, so the attack stalls. Holds no
     * thread: the shared scheduler sends each drip, and the connection is closed when the
     * peer goes away or the maximum hold time is reached.
     */
    private void tarpit(Socket socket, String src) {
        tarpitted.add(socket);
        long deadline = System.nanoTime() + limits.tarpitMaxHold().toNanos();
        Drip drip = new Drip(socket, src, deadline);
        try {
            drip.future = tarpitDrips.scheduleWithFixedDelay(drip, 0, limits.tarpitDrip().toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            drip.finish(); // decoy stopping
        }
    }

    /** One tarpitted connection: each run sends one byte, until the hold ends or the peer leaves. */
    private final class Drip implements Runnable {
        private final Socket socket;
        private final String src;
        private final long deadline;
        private volatile ScheduledFuture<?> future;
        private boolean finished; // drip thread only, apart from the rejected-start case

        Drip(Socket socket, String src, long deadline) {
            this.socket = socket;
            this.src = src;
            this.deadline = deadline;
        }

        @Override
        public void run() {
            if (finished) {
                return;
            }
            if (!running || socket.isClosed() || System.nanoTime() - deadline >= 0) {
                finish();
                return;
            }
            try {
                OutputStream out = socket.getOutputStream();
                out.write('.');
                out.flush();
            } catch (IOException e) {
                finish();
            }
        }

        void finish() {
            finished = true;
            ScheduledFuture<?> f = future;
            if (f != null) {
                f.cancel(false);
            }
            tarpitted.remove(socket);
            closeQuietly(socket);
            release(src);
        }
    }

    /** Count one more open connection for {@code src}, unless it is already at the cap. */
    private boolean acquire(String src) {
        boolean[] ok = {false};
        perSource.compute(src, (k, n) -> {
            int open = n == null ? 0 : n;
            if (open >= limits.maxPerSource()) {
                return n;
            }
            ok[0] = true;
            return open + 1;
        });
        return ok[0];
    }

    private void release(String src) {
        perSource.computeIfPresent(src, (k, n) -> n <= 1 ? null : n - 1);
    }

    /** Close with a reset; report it, at most once per source per {@link #LIMIT_REPORT_EVERY_MS}. */
    private void refuse(Socket socket, String src, int srcPort, Consumer<AttackEvent> sink, String reason) {
        closeWithReset(socket);
        long now = System.currentTimeMillis();
        Long last = lastLimitReport.get(src);
        if (last == null || now - last >= LIMIT_REPORT_EVERY_MS) {
            lastLimitReport.put(src, now);
            sink.accept(new AttackEvent(src, srcPort, type, Action.CONNECT, reason, Instant.now()));
        }
    }

    /** Open connections currently counted for {@code src} (served + tarpitted). */
    public int openConnections(String src) {
        return perSource.getOrDefault(src, 0);
    }

    public void stop() {
        running = false;
        if (workers != null) {
            workers.shutdownNow();
        }
        if (tarpitDrips != null) {
            tarpitDrips.shutdownNow();
        }
        for (Socket s : tarpitted) {
            closeQuietly(s);
        }
        tarpitted.clear();
        try {
            if (server != null) {
                server.close(); // unblock accept()
            }
        } catch (IOException e) {
            // already closed
        }
    }

    /** The client's input as lines of at most {@link #MAX_LINE_BYTES}; subclasses read through this. */
    protected static BoundedLineReader lines(Socket socket) throws IOException {
        return new BoundedLineReader(socket.getInputStream(), MAX_LINE_BYTES);
    }

    protected AttackEvent event(String sourceIp, int sourcePort, Action action, String payload) {
        return new AttackEvent(sourceIp, sourcePort, type, action, payload, Instant.now());
    }

    protected abstract void interact(Socket socket, String sourceIp, int sourcePort,
                                     Consumer<AttackEvent> sink) throws IOException;

    private static void closeWithReset(Socket socket) {
        try {
            socket.setSoLinger(true, 0);
        } catch (IOException e) {
            // already closed
        }
        closeQuietly(socket);
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // already closed
        }
    }

    private static ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }
}
