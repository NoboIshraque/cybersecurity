package com.mirage.specter;

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
    public void run(Target target) {
        for (String user : usernames) {
            attempt(target, user, password);
        }
    }

    private void attempt(Target target, String user, String pass) {
        try (Socket socket = new Socket(target.host(), target.port())) {
            String creds = Base64.getEncoder()
                    .encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
            String request = "GET / HTTP/1.0\r\nAuthorization: Basic " + creds + "\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // decoy may close the socket; move on
        }
    }

    @Override
    public String name() {
        return "password-spray";
    }
}
