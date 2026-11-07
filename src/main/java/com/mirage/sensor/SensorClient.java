package com.mirage.sensor;

import com.mirage.common.AttackEvent;
import com.mirage.common.JsonCodec;
import com.mirage.common.ResponseCommand;
import com.mirage.common.SensorMessage;

import javax.net.SocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * The Sensor side of the link: sends events and heartbeats to the Console, and
 * (via {@link #startReceiving}) reads active-defence commands the Console pushes
 * back. Pass a plain {@link SocketFactory} for local/dev, or an SSL one for TLS.
 */
public final class SensorClient implements AutoCloseable {

    private final String sensorId;
    private final Socket socket;
    private final Writer out;

    public SensorClient(String sensorId, SocketFactory socketFactory, String host, int port)
            throws IOException {
        this.sensorId = sensorId;
        this.socket = socketFactory.createSocket(host, port);
        this.out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
    }

    public synchronized void sendEvent(AttackEvent event) throws IOException {
        writeLine(JsonCodec.encode(SensorMessage.event(event)));
    }

    public synchronized void sendHeartbeat() throws IOException {
        writeLine(JsonCodec.encode(SensorMessage.heartbeat(sensorId)));
    }

    /** Confirm to the Console that a response command has been applied on this Sensor. */
    public synchronized void sendAck(ResponseCommand applied) throws IOException {
        writeLine(JsonCodec.encode(SensorMessage.ack(sensorId, applied)));
    }

    /** Start a background thread that applies each command the Console sends. */
    public void startReceiving(Consumer<ResponseCommand> handler) {
        Thread t = new Thread(() -> {
            try {
                receive(handler);
            } catch (IOException e) {
                // link closed
            }
        }, "sensor-client-rx");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Apply each command the Console sends, on the calling thread, until the link closes
     * (returns) or fails (throws). A line that can't be decoded or applied is skipped, so one
     * bad or newer-format command doesn't end the link (B-13).
     */
    public void receive(Consumer<ResponseCommand> handler) throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            try {
                handler.accept(JsonCodec.decodeCommand(line));
            } catch (RuntimeException e) {
                System.out.println("[link] skipped a command the Sensor could not apply: " + e.getMessage());
            }
        }
    }

    private void writeLine(String json) throws IOException {
        out.write(json);
        out.write('\n');
        out.flush();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
