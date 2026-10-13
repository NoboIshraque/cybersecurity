package com.mirage.sensor;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

/** A fake "router admin" login page. Captures Basic-auth attempts; always 401s. */
public final class FakeHttpLoginDecoy extends Decoy {

    public FakeHttpLoginDecoy(int port) {
        super("http-login", port);
    }

    @Override
    protected void interact(Socket socket, String sourceIp, int sourcePort,
                            Consumer<AttackEvent> sink) throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String credentials = null;
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            if (line.regionMatches(true, 0, "Authorization: Basic ", 0, 21)) {
                credentials = decodeBasic(line.substring(21).trim());
            }
        }
        if (credentials != null) {
            sink.accept(event(sourceIp, sourcePort, Action.LOGIN_ATTEMPT, credentials));
        }
        String page = "HTTP/1.1 401 Unauthorized\r\n"
                    + "WWW-Authenticate: Basic realm=Router-Admin\r\n\r\n";
        socket.getOutputStream().write(page.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeBasic(String base64) {
        try {
            return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
