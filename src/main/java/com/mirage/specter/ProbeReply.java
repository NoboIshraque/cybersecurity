package com.mirage.specter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads just enough of a decoy's reply to tell honestly what happened to a probe,
 * so the attack shell can show a real outcome rather than a guess:
 *
 * <ul>
 *   <li>{@code ACCEPTED} — the decoy returned a success line (it let us "in").</li>
 *   <li>{@code REJECTED} — the decoy answered with a normal failure (401/530/...).</li>
 *   <li>{@code DROPPED}  — the connection was reset (an active block: a blocked decoy rejects
 *       with a TCP reset, like a firewall).</li>
 *   <li>{@code UNREACHABLE} — no usable connection at all: refused (nothing listening), timed
 *       out (host down, or a firewall silently dropping packets) or no route. Not a block: on
 *       two laptops this usually means a set-up problem, so it must not read as one (FB-01).</li>
 *   <li>{@code NO_REPLY} — the other side closed quietly without answering (nothing there that
 *       speaks the protocol, e.g. the attacker's accept-and-log counter-watch).</li>
 *   <li>{@code STALLED}  — no reply before the timeout (a tarpit holding the line).</li>
 * </ul>
 *
 * <p>A reset counts as a block whenever it shows up: while connecting, while sending the
 * request, or while reading the reply. Which of those it hits depends only on how fast the
 * decoy resets, so treating them differently made the outcome a race (B-24).
 */
final class ProbeReply {

    enum Outcome { ACCEPTED, REJECTED, DROPPED, NO_REPLY, STALLED, UNREACHABLE }

    /**
     * How long to wait for a connection. Without it a silent host (wrong IP, a firewall dropping
     * packets) hangs for the OS default, about 21 s on Windows and minutes on Linux (FB-01).
     */
    static final int CONNECT_TIMEOUT_MS = 3000;

    /** Most of a granted page that is read for its file list (FB-06). */
    static final int MAX_PAGE_CHARS = 16_384;
    /** Most file names taken from one page. */
    static final int MAX_LISTED = 20;
    /** A file entry on a granted page: a {@code <h2>} heading or a {@code <li>} item. */
    private static final Pattern PAGE_ITEM = Pattern.compile("<(h2|li)>([^<]{1,200})</\\1>");

    final Outcome outcome;
    final String firstLine;
    /** Why an {@code UNREACHABLE} probe failed, in plain words; empty otherwise. */
    final String reason;
    /** The file names a granted HTTP page shows (FB-06); empty for anything else. */
    final List<String> listing;

    private ProbeReply(Outcome outcome, String firstLine) {
        this(outcome, firstLine, "", List.of());
    }

    private ProbeReply(Outcome outcome, String firstLine, String reason) {
        this(outcome, firstLine, reason, List.of());
    }

    private ProbeReply(Outcome outcome, String firstLine, String reason, List<String> listing) {
        this.outcome = outcome;
        this.firstLine = firstLine;
        this.reason = reason;
        this.listing = listing;
    }

    /** True when the probe never got a usable connection (refused, unreachable, ...), not a reset. */
    boolean connectFailed() {
        return outcome == Outcome.UNREACHABLE;
    }

    /** Connect, send {@code request}, read the reply; every failure on the way is classified. */
    static ProbeReply probe(Target target, byte[] request, int timeoutMillis) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(target.host(), target.port()), CONNECT_TIMEOUT_MS);
        } catch (IOException e) {
            closeQuietly(socket);
            return failure(e);
        }
        try (socket) {
            return exchange(socket, request, timeoutMillis);
        } catch (IOException e) {
            return failure(e); // only close() can throw here
        }
    }

    /** Send {@code request} on an open socket and read the reply. */
    static ProbeReply exchange(Socket socket, byte[] request, int timeoutMillis) {
        try {
            OutputStream out = socket.getOutputStream();
            out.write(request);
            out.flush();
        } catch (IOException e) {
            return failure(e); // the decoy's reset arrived before (or while) we wrote
        }
        return read(socket, timeoutMillis);
    }

    /** A probe that failed before any reply: a reset or abort is a block, anything else a failed connection. */
    static ProbeReply failure(IOException e) {
        return isReset(e) ? new ProbeReply(Outcome.DROPPED, "")
                : new ProbeReply(Outcome.UNREACHABLE, "", reasonFor(e));
    }

    /** A short, plain reason for a failed connection, so the presenter can tell set-up problems apart. */
    static String reasonFor(IOException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        if (e instanceof SocketTimeoutException || message.contains("timed out")) {
            return "timed out: host down, or a firewall dropping packets";
        }
        if (message.contains("refused")) {
            return "refused: nothing listening on that port";
        }
        if (message.contains("unreachable") || message.contains("no route")) {
            return "no route to that host";
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing was connected
        }
    }

    /** The peer reset or aborted the connection (Windows and Linux/JDK wordings). */
    static boolean isReset(IOException e) {
        if (!(e instanceof SocketException) || e instanceof ConnectException) {
            return false; // refused / unreachable: nothing to do with a block
        }
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("reset") || message.contains("abort") || message.contains("broken pipe");
    }

    /** {@code HTTP/1.1 200 OK}: the version, then the three-digit status code as its own token. */
    private static final Pattern HTTP_STATUS = Pattern.compile("^HTTP/\\d(?:\\.\\d)?\\s+(\\d{3})(?:\\s.*)?$");
    /** {@code 230 Login successful}, {@code 230-Welcome}: an FTP-style reply code at the start of the line. */
    private static final Pattern REPLY_CODE = Pattern.compile("^(\\d{3})(?:[ -].*)?$");

    /**
     * True only when the line's status code says "you're in": an HTTP 2xx status line, or the
     * FTP 230 "user logged in" reply. The code is read from its fixed place at the start of the
     * line, so digits elsewhere (a username, a timeout, a counter) can't turn a failure into
     * a success (B-19).
     */
    static boolean isSuccessLine(String line) {
        Matcher http = HTTP_STATUS.matcher(line);
        if (http.matches()) {
            return http.group(1).charAt(0) == '2';
        }
        Matcher code = REPLY_CODE.matcher(line);
        return code.matches() && code.group(1).equals("230");
    }

    /**
     * FB-06: the file names on a granted page (the rest of the reply after its status line), so
     * the attack shell can show what the attacker "got". The read is bounded and best-effort:
     * the outcome is already ACCEPTED, so a page cut short or a stall only shortens the list.
     */
    static List<String> listing(BufferedReader in) {
        StringBuilder page = new StringBuilder();
        char[] buffer = new char[2048];
        try {
            int read;
            while (page.length() < MAX_PAGE_CHARS && (read = in.read(buffer)) > 0) {
                page.append(buffer, 0, read);
                if (page.indexOf("</html>") >= 0) {
                    break; // the decoy's pages end here; don't wait for the close
                }
            }
        } catch (IOException e) {
            // keep what arrived
        }
        return listing(page.length() > MAX_PAGE_CHARS ? page.substring(0, MAX_PAGE_CHARS) : page.toString());
    }

    /** The {@code <h2>} / {@code <li>} entries of an HTML page, unescaped, at most {@link #MAX_LISTED}. */
    static List<String> listing(String page) {
        List<String> names = new ArrayList<>();
        Matcher m = PAGE_ITEM.matcher(page);
        while (m.find() && names.size() < MAX_LISTED) {
            String name = m.group(2).replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").strip();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    static ProbeReply read(Socket socket, int timeoutMillis) {
        try {
            socket.setSoTimeout(timeoutMillis);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line = in.readLine();
            if (line == null) {
                return new ProbeReply(Outcome.NO_REPLY, ""); // orderly close, nothing said
            }
            String trimmed = line.trim();
            if (!isSuccessLine(trimmed)) {
                return new ProbeReply(Outcome.REJECTED, trimmed);
            }
            List<String> listing = HTTP_STATUS.matcher(trimmed).matches() ? listing(in) : List.of();
            return new ProbeReply(Outcome.ACCEPTED, trimmed, "", listing);
        } catch (SocketTimeoutException e) {
            return new ProbeReply(Outcome.STALLED, "");
        } catch (IOException e) {
            return new ProbeReply(Outcome.DROPPED, ""); // connection reset: actively rejected
        }
    }
}
