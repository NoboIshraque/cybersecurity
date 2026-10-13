package com.mirage.specter;

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
    public void run(Target target) {
        for (String password : passwords) {
            try (Socket socket = new Socket(target.host(), target.port())) {
                String creds = Base64.getEncoder()
                        .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
                String request = "GET / HTTP/1.0\r\nAuthorization: Basic " + creds + "\r\n\r\n";
                OutputStream out = socket.getOutputStream();
                out.write(request.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException ignored) {
                // decoy may close the socket; move on to the next password
            }
        }
    }

    @Override
    public String name() {
        return "brute-force";
    }
}
