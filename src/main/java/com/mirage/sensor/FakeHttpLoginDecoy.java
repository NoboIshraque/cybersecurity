package com.mirage.sensor;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.BoundedLineReader;

import java.io.IOException;
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

    /** Header lines read before the decoy gives up on a request. */
    public static final int MAX_HEADER_LINES = 50;

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
        Request request = readRequest(lines(socket));
        if (request == null) {
            return; // not a real client; hang up without answering
        }
        String credentials = request.credentials();
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

    /**
     * Fed fake data (B-09): any credential "works" and opens a fake file manager showing the
     * lure files. Without credentials the page still asks for them, as a real one would. Only
     * the trap credential counts as a break-in; a wrong password let in by the deception is
     * just a login attempt.
     */
    @Override
    protected void feedFake(Socket socket, String sourceIp, int sourcePort,
                            Consumer<AttackEvent> sink) throws IOException {
        Request request = readRequest(lines(socket));
        if (request == null) {
            return;
        }
        String credentials = request.credentials();
        if (credentials == null) {
            writeResponse(socket, "HTTP/1.1 401 Unauthorized\r\n"
                    + "WWW-Authenticate: Basic realm=Router-Admin\r\n\r\n");
            return;
        }
        sink.accept(event(sourceIp, sourcePort, Action.LOGIN_ATTEMPT, credentials));
        if (trapCredentials.contains(credentials)) {
            sink.accept(event(sourceIp, sourcePort, Action.DECOY_ACCESS, credentials));
        }
        writeResponse(socket, fakeFilesPage());
    }

    /** The request's Basic-auth credentials (null if none); null request if it has too many headers. */
    private static Request readRequest(BoundedLineReader in) throws IOException {
        String credentials = null;
        String line;
        int read = 0;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            if (++read > MAX_HEADER_LINES + 1) { // + the request line
                return null;
            }
            if (line.regionMatches(true, 0, "Authorization: Basic ", 0, 21)) {
                credentials = decodeBasic(line.substring(21).trim());
            }
        }
        return new Request(credentials);
    }

    private record Request(String credentials) {
    }

    private static String fakeFilesPage() {
        StringBuilder body = new StringBuilder("<html><head><title>Router Admin</title></head><body>"
                + "<h1>NovaTel RG-9000 &mdash; File Manager</h1>"
                + "<p>Welcome, administrator. Firmware 2.1.7.</p>");
        lureFiles().forEach((path, content) -> body.append("<h2>").append(escape(path)).append("</h2><pre>")
                .append(escape(content)).append("</pre>"));
        body.append("</body></html>");
        return "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Length: " + body.toString().getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n"
                + body;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
