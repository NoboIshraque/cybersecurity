package com.mirage;

import com.mirage.specter.AttackTrace;
import com.mirage.specter.HoneytokenReplayAttack;
import com.mirage.specter.Target;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-19: the outcome must come from the reply's status code, not from "200"/"230" appearing
 * anywhere in the line. Each case runs the real replay strategy against a stub server that
 * answers with one fixed first line.
 */
class ProbeStatusParsingTest {

    /** Failures that merely contain the digits 200 or 230 somewhere in the text. */
    @ParameterizedTest
    @ValueSource(strings = {
            "331 Password required for admin2001",
            "HTTP/1.1 401 Unauthorized (retry in 1200 ms)",
            "530 Login incorrect, 2 attempts left of 230",
            "HTTP/1.1 403 Forbidden 200",
            "HTTP/1.1 302 Found",
            "Welcome, 200 users online",
            "2300 is not a status code",
            "HTTP/1.1 2000 OK",
    })
    void failureContainingTheDigitsIsNoAccess(String firstLine) throws Exception {
        List<String> lines = replayAgainst(firstLine);
        assertTrue(lines.contains("BANNER NO ACCESS"), lines.toString());
        assertTrue(lines.stream().noneMatch(l -> l.contains("ACCESS GRANTED")), lines.toString());
    }

    /** Real success lines: an HTTP 2xx status line, or FTP's 230 "logged in". */
    @ParameterizedTest
    @ValueSource(strings = {
            "HTTP/1.1 200 OK",
            "HTTP/1.0 200 OK",
            "HTTP/1.1 204 No Content",
            "230 Login successful",
            "230-Welcome",
            "230",
    })
    void realSuccessLineIsAccessGranted(String firstLine) throws Exception {
        List<String> lines = replayAgainst(firstLine);
        assertTrue(lines.contains("BANNER ACCESS GRANTED"), lines.toString());
    }

    private static List<String> replayAgainst(String firstLine) throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread stub = new Thread(() -> answerOnce(server, firstLine), "probe-stub");
            stub.setDaemon(true);
            stub.start();
            List<String> lines = new ArrayList<>();
            AttackTrace trace = (level, text) -> lines.add(level + " " + text);
            new HoneytokenReplayAttack("svc_backup:S3cr3tBackup!")
                    .run(new Target("127.0.0.1", server.getLocalPort()), trace);
            stub.join(5_000);
            return lines;
        }
    }

    /** Read the whole request first (so closing doesn't reset), then send one line and close. */
    private static void answerOnce(ServerSocket server, String firstLine) {
        try (Socket client = server.accept()) {
            client.setSoTimeout(3_000);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            String header;
            while ((header = in.readLine()) != null && !header.isEmpty()) {
                // drain the request headers
            }
            OutputStream out = client.getOutputStream();
            out.write((firstLine + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            client.shutdownOutput();
            in.read(); // wait for the client to close
        } catch (Exception ignored) {
            // the test's assertions report what the client saw
        }
    }
}
