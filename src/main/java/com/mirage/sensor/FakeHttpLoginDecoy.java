package com.mirage.sensor;

import com.mirage.common.AttackEvent;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** A fake "router admin" login page. Always 401s; captures every attempt. */
public final class FakeHttpLoginDecoy extends Decoy {

    public FakeHttpLoginDecoy(int port) {
        super("http-login", port);
    }

    @Override
    protected void respond(Socket socket, AttackEvent event) throws IOException {
        String page = "HTTP/1.1 401 Unauthorized\r\n"
                    + "WWW-Authenticate: Basic realm=Router-Admin\r\n\r\n";
        socket.getOutputStream().write(page.getBytes(StandardCharsets.UTF_8));
    }
}
