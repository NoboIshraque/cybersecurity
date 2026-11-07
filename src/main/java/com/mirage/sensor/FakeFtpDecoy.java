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

    /**
     * Fed fake data (B-09): any password "works" (230), then a short, believable session over
     * canned text. STAT lists the lure files on the control connection; anything that needs a
     * data connection is refused, as a server behind a strict firewall would. The session ends
     * on QUIT, after {@link #MAX_FAKE_COMMANDS}, or on the decoy's read timeout.
     */
    @Override
    protected void feedFake(Socket socket, String sourceIp, int sourcePort,
                            Consumer<AttackEvent> sink) throws IOException {
        OutputStream out = socket.getOutputStream();
        BoundedLineReader in = lines(socket);
        send(out, "220 FTP server ready");

        String user = null;
        boolean in230 = false;
        String line;
        int commands = 0;
        while ((line = in.readLine()) != null && ++commands <= MAX_FAKE_COMMANDS) {
            String upper = line.trim().toUpperCase();
            if (upper.startsWith("QUIT")) {
                send(out, "221 Goodbye");
                return;
            }
            if (!in230) {
                if (upper.startsWith("USER ")) {
                    user = line.substring(5).trim();
                    send(out, "331 Password required for " + user);
                } else if (upper.startsWith("PASS ")) {
                    sink.accept(event(sourceIp, sourcePort, Action.LOGIN_ATTEMPT,
                            (user == null ? "" : user) + ":" + line.substring(5).trim()));
                    send(out, "230 Login successful");
                    in230 = true;
                } else {
                    send(out, "530 Please login with USER and PASS");
                }
                continue;
            }
            send(out, fakeReply(upper));
        }
    }

    /** Commands a fed session may send before the decoy hangs up. */
    static final int MAX_FAKE_COMMANDS = 50;

    private static String fakeReply(String upper) {
        if (upper.startsWith("PWD") || upper.startsWith("XPWD")) {
            return "257 \"/home/backup\" is the current directory";
        }
        if (upper.startsWith("SYST")) {
            return "215 UNIX Type: L8";
        }
        if (upper.startsWith("CWD") || upper.startsWith("CDUP")) {
            return "250 Directory successfully changed";
        }
        if (upper.startsWith("TYPE")) {
            return "200 Switching to Binary mode";
        }
        if (upper.startsWith("STAT")) {
            StringBuilder listing = new StringBuilder("213-Status of /home/backup:\r\n");
            lureFiles().forEach((path, content) -> listing.append(String.format(
                    " -rw-r--r--   1 backup   backup   %6d Nov 02 02:00 %s\r\n",
                    content.getBytes(StandardCharsets.UTF_8).length, path)));
            return listing.append("213 End of status").toString();
        }
        if (upper.startsWith("LIST") || upper.startsWith("NLST") || upper.startsWith("RETR")
                || upper.startsWith("PASV") || upper.startsWith("EPSV") || upper.startsWith("PORT")) {
            return "425 Can't open data connection";
        }
        return "502 Command not implemented";
    }

    private static void send(OutputStream out, String line) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
