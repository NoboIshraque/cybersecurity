package com.mirage.specter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Reads just enough of a decoy's reply to tell honestly what happened to a probe,
 * so the attack shell can show a real outcome rather than a guess:
 *
 * <ul>
 *   <li>{@code ACCEPTED} — the decoy returned a success line (it let us "in").</li>
 *   <li>{@code REJECTED} — the decoy answered with a normal failure (401/530/...).</li>
 *   <li>{@code DROPPED}  — the connection was reset (an active block: a blocked decoy rejects
 *       with a TCP reset, like a firewall), or could not be used at all.</li>
 *   <li>{@code NO_REPLY} — the other side closed quietly without answering (nothing there that
 *       speaks the protocol, e.g. the attacker's accept-and-log counter-watch).</li>
 *   <li>{@code STALLED}  — no reply before the timeout (a tarpit holding the line).</li>
 * </ul>
 */
final class ProbeReply {

    enum Outcome { ACCEPTED, REJECTED, DROPPED, NO_REPLY, STALLED }

    final Outcome outcome;
    final String firstLine;

    private ProbeReply(Outcome outcome, String firstLine) {
        this.outcome = outcome;
        this.firstLine = firstLine;
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
