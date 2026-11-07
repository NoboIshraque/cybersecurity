package com.mirage.sensor;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A fake "router admin" login page. It captures Basic-auth attempts and, for the
 * overwhelming majority, answers 401 — exactly as before. The twist for the demo:
 * a small set of deliberately planted "trap" credentials (a honeytoken, say) are
 * ACCEPTED, returning a static fake admin page. Nothing real is behind it; the
 * page is fixed text. The grant is reported as a {@link Action#DECOY_ACCESS} event
 * so the Console can show the intruder "inside" the honeypot and respond.
 */
public final class FakeHttpLoginDecoy extends Decoy {

    /** Credentials the decoy pretends are valid, so an intruder believes they got in. */
    private static final Set<String> DEFAULT_TRAP_CREDENTIALS = Set.of("svc_backup:S3cr3tBackup!");

    private final Set<String> trapCredentials;

    public FakeHttpLoginDecoy(int port) {
        this(port, DEFAULT_TRAP_CREDENTIALS);
    }

    public FakeHttpLoginDecoy(int port, Set<String> trapCredentials) {
        super("http-login", port);
        this.trapCredentials = Set.copyOf(trapCredentials);
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

        boolean granted = credentials != null && trapCredentials.contains(credentials);
        if (credentials != null) {
            sink.accept(event(sourceIp, sourcePort, Action.LOGIN_ATTEMPT, credentials));
        }
        if (granted) {
            // The intruder is now "inside" the decoy — a high-signal honeypot session.
            sink.accept(event(sourceIp, sourcePort, Action.DECOY_ACCESS, credentials));
            writeResponse(socket, grantedPage());
        } else {
            writeResponse(socket, "HTTP/1.1 401 Unauthorized\r\n"
                    + "WWW-Authenticate: Basic realm=Router-Admin\r\n\r\n");
        }
    }

    private static String grantedPage() {
        String body = "<html><head><title>Router Admin</title></head><body>"
                + "<h1>NovaTel RG-9000 &mdash; Admin Console</h1>"
                + "<p>Welcome, administrator. Firmware 2.1.7.</p>"
                + "<ul><li>config/backup.cfg</li><li>config/vpn.key</li><li>users.db</li></ul>"
                + "</body></html>";
        return "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n"
                + body;
    }

    private static void writeResponse(Socket socket, String response) throws IOException {
        socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
    }

    private static String decodeBasic(String base64) {
        try {
            return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
