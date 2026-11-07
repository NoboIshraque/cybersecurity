package com.mirage.sensor;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.BoundedLineReader;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** A fake FTP service. Speaks just enough of the protocol to capture logins. */
public final class FakeFtpDecoy extends Decoy {

    public FakeFtpDecoy(int port) {
        super("ftp", port);
    }

    @Override
    protected void interact(Socket socket, String sourceIp, int sourcePort,
                            Consumer<AttackEvent> sink) throws IOException {
        OutputStream out = socket.getOutputStream();
        BoundedLineReader in = lines(socket);
        send(out, "220 FTP server ready");

        String user = null;
        String line;
        while ((line = in.readLine()) != null) {
            String upper = line.toUpperCase();
            if (upper.startsWith("USER ")) {
                user = line.substring(5).trim();
                send(out, "331 Password required for " + user);
            } else if (upper.startsWith("PASS ")) {
                String pass = line.substring(5).trim();
                sink.accept(event(sourceIp, sourcePort, Action.LOGIN_ATTEMPT,
                        (user == null ? "" : user) + ":" + pass));
                send(out, "530 Login incorrect");
                break;
            } else if (upper.startsWith("QUIT")) {
                send(out, "221 Goodbye");
                break;
            } else {
                send(out, "500 Unknown command");
            }
        }
    }

    private static void send(OutputStream out, String line) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
