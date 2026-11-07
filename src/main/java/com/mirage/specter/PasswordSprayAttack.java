package com.mirage.specter;

import com.mirage.common.trace.TraceLevel;

import java.util.List;

/** Tries one password across many usernames against a Basic-auth login decoy. */
public final class PasswordSprayAttack implements AttackStrategy {

    private final List<String> usernames;
    private final String password;

    public PasswordSprayAttack(List<String> usernames, String password) {
        this.usernames = usernames;
        this.password = password;
    }

    @Override
    public void run(Target target, AttackTrace trace) {
        trace.line(TraceLevel.COMMAND, "spray --pass '" + password + "' --users "
                + String.join(",", usernames) + "  " + target.host() + ":" + target.port());
        ProbeReply.Outcome last = ProbeReply.Outcome.REJECTED;
        for (String user : usernames) {
            last = attempt(target, user, password, trace);
            if (last == ProbeReply.Outcome.ACCEPTED) {
                trace.line(TraceLevel.GOOD, "valid credential found: " + user + ":" + password);
                break;
            }
            if (last == ProbeReply.Outcome.DROPPED || last == ProbeReply.Outcome.STALLED
                    || last == ProbeReply.Outcome.NO_REPLY) {
                break;
            }
        }
        if (last == ProbeReply.Outcome.REJECTED) {
            trace.line(TraceLevel.WARN, "spray complete, no account accepted the password");
        }
        trace.line(TraceLevel.BANNER, BruteForceAttack.bannerFor(last));
    }

    private ProbeReply.Outcome attempt(Target target, String user, String pass, AttackTrace trace) {
        ProbeReply reply = ProbeReply.probe(target, BruteForceAttack.basicAuthRequest(user + ":" + pass), 1500);
        trace.line(BruteForceAttack.levelFor(reply.outcome),
                "try " + user + ":" + pass + "  ->  " + BruteForceAttack.describe(reply));
        return reply.outcome;
    }

    @Override
    public String name() {
        return "password-spray";
    }
}
