package com.mirage.specter;

import com.mirage.common.trace.TraceLevel;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** Tries a password list for one username against a Basic-auth login decoy. */
public final class BruteForceAttack implements AttackStrategy {

    private final String username;
    private final List<String> passwords;

    public BruteForceAttack(List<String> passwords) {
        this("admin", passwords);
    }

    public BruteForceAttack(String username, List<String> passwords) {
        this.username = username;
        this.passwords = passwords;
    }

    @Override
    public void run(Target target, AttackTrace trace) {
        trace.line(TraceLevel.COMMAND, "hydra -l " + username + " -P passwords.txt "
                + target.host() + ":" + target.port() + " http-get");
        ProbeReply.Outcome last = ProbeReply.Outcome.REJECTED;
        for (String password : passwords) {
            last = attempt(target, username, password, trace);
            if (last == ProbeReply.Outcome.ACCEPTED) {
                trace.line(TraceLevel.GOOD, "valid credential found: " + username + ":" + password);
                break;
            }
            if (last == ProbeReply.Outcome.DROPPED || last == ProbeReply.Outcome.STALLED) {
                break; // blocked or tarpitted — no point hammering further
            }
        }
        if (last == ProbeReply.Outcome.REJECTED) {
            trace.line(TraceLevel.WARN, "password list exhausted, no valid credential");
        }
        trace.line(TraceLevel.BANNER, bannerFor(last));
    }

    private ProbeReply.Outcome attempt(Target target, String user, String pass, AttackTrace trace) {
        try (Socket socket = new Socket(target.host(), target.port())) {
            String creds = Base64.getEncoder()
                    .encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
            String request = "GET / HTTP/1.0\r\nAuthorization: Basic " + creds + "\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            ProbeReply reply = ProbeReply.read(socket, 1500);
            trace.line(levelFor(reply.outcome), "try " + user + ":" + pass + "  ->  " + describe(reply));
            return reply.outcome;
        } catch (IOException e) {
            trace.line(TraceLevel.BAD, "try " + user + ":" + pass + "  ->  connection failed");
            return ProbeReply.Outcome.DROPPED;
        }
    }

    static TraceLevel levelFor(ProbeReply.Outcome outcome) {
        return switch (outcome) {
            case ACCEPTED -> TraceLevel.GOOD;
            case REJECTED -> TraceLevel.INFO;
            case DROPPED, STALLED -> TraceLevel.BAD;
        };
    }

    static String describe(ProbeReply reply) {
        return switch (reply.outcome) {
            case ACCEPTED -> "200 OK  ACCESS GRANTED";
            case REJECTED -> reply.firstLine.isEmpty() ? "rejected" : reply.firstLine;
            case DROPPED -> "connection dropped (blocked)";
            case STALLED -> "no reply (tarpit)";
        };
    }

    /** The end-of-run banner text for an outcome, shared by the credential strategies. */
    static String bannerFor(ProbeReply.Outcome outcome) {
        return switch (outcome) {
            case ACCEPTED -> "ACCESS GRANTED";
            case REJECTED -> "NO ACCESS";
            case DROPPED -> "CONNECTION BLOCKED";
            case STALLED -> "CONNECTION STALLED";
        };
    }

    @Override
    public String name() {
        return "brute-force";
    }
}
