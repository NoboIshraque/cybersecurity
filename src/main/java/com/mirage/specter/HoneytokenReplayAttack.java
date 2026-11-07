package com.mirage.specter;

import com.mirage.common.trace.TraceLevel;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
        try (Socket socket = new Socket(target.host(), target.port())) {
            String creds = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            String request = "GET / HTTP/1.0\r\nAuthorization: Basic " + creds + "\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            ProbeReply reply = ProbeReply.read(socket, 1500);
            trace.line(BruteForceAttack.levelFor(reply.outcome),
                    "replay -> " + BruteForceAttack.describe(reply));
            trace.line(TraceLevel.BANNER, BruteForceAttack.bannerFor(reply.outcome));
        } catch (IOException e) {
            trace.line(TraceLevel.BAD, "replay -> connection failed");
            trace.line(TraceLevel.BANNER, "CONNECTION BLOCKED");
        }
    }

    @Override
    public String name() {
        return "honeytoken-replay";
    }
}
