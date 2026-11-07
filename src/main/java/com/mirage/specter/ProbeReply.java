package com.mirage.specter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Reads just enough of a decoy's reply to tell honestly what happened to a probe,
 * so the attack shell can show a real outcome rather than a guess:
 *
 * <ul>
 *   <li>{@code ACCEPTED} — the decoy returned a success line (it let us "in").</li>
 *   <li>{@code REJECTED} — the decoy answered with a normal failure (401/530/...).</li>
 *   <li>{@code DROPPED}  — the connection was reset (an active block: a blocked decoy rejects
 *       with a TCP reset, like a firewall), or could not be used at all
 *       ({@link #connectFailed()}).</li>
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

    enum Outcome { ACCEPTED, REJECTED, DROPPED, NO_REPLY, STALLED }

    final Outcome outcome;
    final String firstLine;
    private final boolean connectFailed;

    private ProbeReply(Outcome outcome, String firstLine) {
        this(outcome, firstLine, false);
    }

    private ProbeReply(Outcome outcome, String firstLine, boolean connectFailed) {
        this.outcome = outcome;
        this.firstLine = firstLine;
        this.connectFailed = connectFailed;
    }

    /** True when the probe never got a usable connection (refused, unreachable, ...), not a reset. */
    boolean connectFailed() {
        return connectFailed;
    }

    /** Connect, send {@code request}, read the reply; every failure on the way is classified. */
    static ProbeReply probe(Target target, byte[] request, int timeoutMillis) {
        Socket socket;
        try {
            socket = new Socket(target.host(), target.port());
        } catch (IOException e) {
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
                : new ProbeReply(Outcome.DROPPED, "", true);
    }

    /** The peer reset or aborted the connection (Windows and Linux/JDK wordings). */
    static boolean isReset(IOException e) {
        if (!(e instanceof SocketException) || e instanceof ConnectException) {
            return false; // refused / unreachable: nothing to do with a block
        }
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("reset") || message.contains("abort") || message.contains("broken pipe");
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
            boolean ok = trimmed.contains("200") || trimmed.toUpperCase().contains("230");
            return new ProbeReply(ok ? Outcome.ACCEPTED : Outcome.REJECTED, trimmed);
        } catch (SocketTimeoutException e) {
            return new ProbeReply(Outcome.STALLED, "");
        } catch (IOException e) {
            return new ProbeReply(Outcome.DROPPED, ""); // connection reset: actively rejected
        }
    }
}
