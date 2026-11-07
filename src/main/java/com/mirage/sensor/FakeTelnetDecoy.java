package com.mirage.sensor;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.BoundedLineReader;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** A fake Telnet login. Prompts for a username and password, then rejects them. */
public final class FakeTelnetDecoy extends Decoy {

    public FakeTelnetDecoy(int port) {
        super("telnet", port);
    }

    @Override
    protected void interact(Socket socket, String sourceIp, int sourcePort,
                            Consumer<AttackEvent> sink) throws IOException {
        OutputStream out = socket.getOutputStream();
        BoundedLineReader in = lines(socket);

        write(out, "login: ");
        String user = in.readLine();
        write(out, "Password: ");
        String pass = in.readLine();

        if (user != null) {
            sink.accept(event(sourceIp, sourcePort, Action.LOGIN_ATTEMPT,
                    user.trim() + ":" + (pass == null ? "" : pass.trim())));
        }
        write(out, "\r\nLogin incorrect\r\n");
    }

    private static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
