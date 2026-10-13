package com.mirage.sensor;

import com.mirage.common.AttackEvent;
import com.mirage.common.JsonCodec;
import com.mirage.common.SensorMessage;

import javax.net.SocketFactory;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * The Sensor side of the link: sends events and heartbeats to the Console as
 * newline-delimited JSON. Pass a plain {@link SocketFactory} for local/dev, or
 * an {@code SSLSocketFactory} built from the lab truststore for TLS.
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
