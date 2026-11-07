package com.mirage.specter;

import com.mirage.common.trace.TraceLevel;

/** Replays a (stolen honeytoken) credential against a Basic-auth login decoy. */
public final class HoneytokenReplayAttack implements AttackStrategy {

    private final String credentials; // "user:pass"

    public HoneytokenReplayAttack(String credentials) {
        this.credentials = credentials;
    }

    @Override
    public void run(Target target, AttackTrace trace) {
        String user = credentials.contains(":") ? credentials.substring(0, credentials.indexOf(':')) : credentials;
        trace.line(TraceLevel.COMMAND, "replay stolen credential '" + user + ":****' at "
                + target.host() + ":" + target.port());
        ProbeReply reply = ProbeReply.probe(target, BruteForceAttack.basicAuthRequest(credentials), 1500);
        trace.line(BruteForceAttack.levelFor(reply.outcome), "replay -> " + BruteForceAttack.describe(reply));
        trace.line(TraceLevel.BANNER, BruteForceAttack.bannerFor(reply.outcome));
    }

    @Override
    public String name() {
        return "honeytoken-replay";
    }
}
