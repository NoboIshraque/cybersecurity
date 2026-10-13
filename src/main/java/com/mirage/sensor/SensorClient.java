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

    /** Start a background thread that applies each command the Console sends. */
    public void startReceiving(Consumer<ResponseCommand> handler) {
        Thread t = new Thread(() -> receiveLoop(handler), "sensor-client-rx");
        t.setDaemon(true);
        t.start();
    }

    private void receiveLoop(Consumer<ResponseCommand> handler) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isBlank()) {
                    handler.accept(JsonCodec.decodeCommand(line));
                }
            }
        } catch (IOException e) {
            // link closed
        } catch (RuntimeException e) {
            // malformed command; ignore
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
