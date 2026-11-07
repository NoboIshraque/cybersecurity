package com.mirage.specter;

import com.mirage.common.trace.TraceLevel;

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
            if (last == ProbeReply.Outcome.DROPPED || last == ProbeReply.Outcome.STALLED
                    || last == ProbeReply.Outcome.NO_REPLY) {
                break; // blocked, tarpitted or nothing answering — no point hammering further
            }
        }
        if (last == ProbeReply.Outcome.REJECTED) {
            trace.line(TraceLevel.WARN, "password list exhausted, no valid credential");
        }
        trace.line(TraceLevel.BANNER, bannerFor(last));
    }

    private ProbeReply.Outcome attempt(Target target, String user, String pass, AttackTrace trace) {
        ProbeReply reply = ProbeReply.probe(target, basicAuthRequest(user + ":" + pass), 1500);
        trace.line(levelFor(reply.outcome), "try " + user + ":" + pass + "  ->  " + describe(reply));
        return reply.outcome;
    }

    /** {@code GET /} with a Basic {@code Authorization} header for {@code credentials} ("user:pass"). */
    static byte[] basicAuthRequest(String credentials) {
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return ("GET / HTTP/1.0\r\nAuthorization: Basic " + encoded + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
    }

    static TraceLevel levelFor(ProbeReply.Outcome outcome) {
        return switch (outcome) {
            case ACCEPTED -> TraceLevel.GOOD;
            case REJECTED -> TraceLevel.INFO;
            case NO_REPLY -> TraceLevel.WARN;
            case DROPPED, STALLED -> TraceLevel.BAD;
        };
    }

    static String describe(ProbeReply reply) {
        return switch (reply.outcome) {
            case ACCEPTED -> "200 OK  ACCESS GRANTED";
            case REJECTED -> reply.firstLine.isEmpty() ? "rejected" : reply.firstLine;
            case DROPPED -> reply.connectFailed() ? "connection failed" : "connection reset (blocked)";
            case NO_REPLY -> "closed without a reply (nothing answering)";
            case STALLED -> "no reply (tarpit)";
        };
    }

    /** The end-of-run banner text for an outcome, shared by the credential strategies. */
    static String bannerFor(ProbeReply.Outcome outcome) {
        return switch (outcome) {
            case ACCEPTED -> "ACCESS GRANTED";
            case REJECTED -> "NO ACCESS";
            case DROPPED -> "CONNECTION BLOCKED";
            case NO_REPLY -> "NO REPLY";
            case STALLED -> "CONNECTION STALLED";
        };
    }

    @Override
    public String name() {
        return "brute-force";
    }
}
