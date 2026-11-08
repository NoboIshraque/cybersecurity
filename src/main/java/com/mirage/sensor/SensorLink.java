package com.mirage.sensor;

import com.mirage.common.AttackEvent;
import com.mirage.common.ResponseCommand;

import javax.net.SocketFactory;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * The Sensor's self-healing link to the Console (B-05). A background thread connects,
 * registers with a heartbeat, delivers any events buffered while the link was down, then
 * applies the Console's commands until the link drops, and starts over with backoff
 * ({@code minBackoff} doubling up to {@code maxBackoff}). So the Console can start after the
 * Sensor, or restart mid-demo, and the Sensor comes back on its own.
 *
 * <p>While down, events are kept in a bounded buffer: past {@code bufferSize} the oldest is
 * dropped and counted. Each applied command is confirmed to the Console with an ACK.
 * All sends are serialised on one lock, so buffered events go out before newer ones.
 */
public final class SensorLink implements AutoCloseable {

    public static final Duration DEFAULT_MIN_BACKOFF = Duration.ofSeconds(1);
    public static final Duration DEFAULT_MAX_BACKOFF = Duration.ofSeconds(10);
    public static final int DEFAULT_BUFFER = 500;

    private final String sensorId;
    private final SocketFactory factory;
    private final String host;
    private final int port;
    private final Consumer<ResponseCommand> handler;
    private final Duration minBackoff;
    private final Duration maxBackoff;
    private final int bufferSize;

    private final Object lock = new Object();
    private final Deque<AttackEvent> pending = new ArrayDeque<>(); // guarded by lock
    private SensorClient client;                                   // guarded by lock
    private long dropped;                                          // guarded by lock
    private volatile boolean connected;
    private volatile boolean closed;
    private Thread thread;

    public SensorLink(String sensorId, SocketFactory factory, String host, int port,
                      Consumer<ResponseCommand> handler) {
        this(sensorId, factory, host, port, handler, DEFAULT_MIN_BACKOFF, DEFAULT_MAX_BACKOFF, DEFAULT_BUFFER);
    }

    public SensorLink(String sensorId, SocketFactory factory, String host, int port,
                      Consumer<ResponseCommand> handler, Duration minBackoff, Duration maxBackoff,
                      int bufferSize) {
        if (bufferSize < 1) {
            throw new IllegalArgumentException("bufferSize must be at least 1");
        }
        this.sensorId = sensorId;
        this.factory = factory;
        this.host = host;
        this.port = port;
        this.handler = handler;
        this.minBackoff = minBackoff;
        this.maxBackoff = maxBackoff;
        this.bufferSize = bufferSize;
    }

    /** Start the background connect / receive / reconnect loop. */
    public synchronized void start() {
        if (thread != null) {
            return;
        }
        thread = new Thread(this::run, "sensor-link");
        thread.setDaemon(true);
        thread.start();
    }

    /** Send an event now, or buffer it until the link is back. Never blocks on a down link. */
    public void sendEvent(AttackEvent event) {
        synchronized (lock) {
            if (client != null) {
                try {
                    client.sendEvent(event);
                    return;
                } catch (IOException e) {
                    dropLink();
                }
            }
            buffer(event);
        }
    }

    /** Heartbeat if connected; a down link is already being retried, so nothing to do otherwise. */
    public void sendHeartbeat() {
        synchronized (lock) {
            if (client != null) {
                try {
                    client.sendHeartbeat();
                } catch (IOException e) {
                    dropLink();
                }
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }

    /** Events discarded because the buffer was full while the link was down. */
    public long droppedCount() {
        synchronized (lock) {
            return dropped;
        }
    }

    @Override
    public void close() {
        closed = true;
        synchronized (lock) {
            dropLink();
        }
        Thread t;
        synchronized (this) {
            t = thread;
        }
        if (t != null) {
            t.interrupt();
        }
    }

    private void run() {
        Duration delay = minBackoff;
        boolean reportedDown = false;
        while (!closed) {
            SensorClient c;
            try {
                c = new SensorClient(sensorId, factory, host, port);
            } catch (IOException e) {
                if (!reportedDown) {
                    System.out.println("[link] Console at " + host + ":" + port + " not reachable ("
                            + e.getMessage() + "); retrying. Decoys keep running and events are buffered.");
                    reportedDown = true;
                }
                if (!pause(delay)) {
                    return;
                }
                delay = next(delay);
                continue;
            }
            int flushed;
            try {
                flushed = register(c);
            } catch (IOException e) {
                closeQuietly(c);
                if (!pause(delay)) {
                    return;
                }
                delay = next(delay);
                continue;
            }
            System.out.println("[link] connected to Console at " + host + ":" + port + " as " + sensorId
                    + (flushed > 0 ? " (sent " + flushed + " buffered event(s))" : ""));
            reportedDown = false;
            delay = minBackoff;
            try {
                c.receive(this::apply);
            } catch (IOException e) {
                // link failed; reconnect below
            } finally {
                synchronized (lock) {
                    if (client == c) {
                        client = null;
                        connected = false;
                    }
                }
                closeQuietly(c);
            }
            if (!closed) {
                System.out.println("[link] lost the Console at " + host + ":" + port + "; reconnecting.");
                reportedDown = true;
                if (!pause(delay)) {
                    return;
                }
            }
        }
    }

    /** Heartbeat first (so the Console knows who this is), then the backlog, then go live. */
    private int register(SensorClient c) throws IOException {
        synchronized (lock) {
            c.sendHeartbeat();
            int flushed = 0;
            while (!pending.isEmpty()) {
                c.sendEvent(pending.peekFirst());
                pending.pollFirst(); // only once it has been written
                flushed++;
            }
            client = c;
            connected = true;
            return flushed;
        }
    }

    private void apply(ResponseCommand command) {
        handler.accept(command);
        synchronized (lock) {
            if (client != null) {
                try {
                    client.sendAck(command); // lets the Console show "applied" in its shell
                } catch (IOException e) {
                    dropLink();
                }
            }
        }
    }

    /** Caller holds {@code lock}. Closing the socket also ends the receive loop, which reconnects. */
    private void dropLink() {
        if (client != null) {
            closeQuietly(client);
            client = null;
        }
        connected = false;
    }

    /** Caller holds {@code lock}. */
    private void buffer(AttackEvent event) {
        if (pending.size() >= bufferSize) {
            pending.pollFirst();
            dropped++;
        }
        pending.addLast(event);
    }

    private Duration next(Duration delay) {
        Duration doubled = delay.multipliedBy(2);
        return doubled.compareTo(maxBackoff) > 0 ? maxBackoff : doubled;
    }

    /** Sleep; false if the link was closed meanwhile. */
    private boolean pause(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return !closed;
    }

    private static void closeQuietly(SensorClient c) {
        try {
            c.close();
        } catch (IOException e) {
            // already closed
        }
    }
}
