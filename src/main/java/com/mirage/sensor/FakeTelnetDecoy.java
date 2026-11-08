package com.mirage.sensor;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.BoundedLineReader;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** A fake Telnet login. Prompts for a username and password, then rejects them. */
public final class FakeTelnetDecoy extends Decoy {

    public FakeTelnetDecoy(int port) {
        super("telnet", port);
    }

    @Override
    protected void interact(Socket socket, String sourceIp, int sourcePort,
                            Consumer<AttackEvent> sink) throws IOException {
        OutputStream out = socket.getOutputStream();
        BoundedLineReader in = lines(socket);

        write(out, "login: ");
        String user = in.readLine();
        write(out, "Password: ");
        String pass = in.readLine();

        if (user != null) {
            sink.accept(event(sourceIp, sourcePort, Action.LOGIN_ATTEMPT,
                    user.trim() + ":" + (pass == null ? "" : pass.trim())));
        }
        write(out, "\r\nLogin incorrect\r\n");
    }

    /** Commands a fed session may send before the decoy hangs up. */
    static final int MAX_FAKE_COMMANDS = 30;

    /**
     * Fed fake data (B-09): any password "works" and opens a fake shell. Every answer is canned
     * text: nothing is ever executed. {@code ls} and {@code cat} show the lure files. The
     * session ends on exit/logout, after {@link #MAX_FAKE_COMMANDS}, or on the read timeout.
     */
    @Override
    protected void feedFake(Socket socket, String sourceIp, int sourcePort,
                            Consumer<AttackEvent> sink) throws IOException {
        OutputStream out = socket.getOutputStream();
        BoundedLineReader in = lines(socket);

        write(out, "login: ");
        String user = in.readLine();
        write(out, "Password: ");
        String pass = in.readLine();
        if (user == null) {
            return;
        }
        String name = user.trim().isEmpty() ? "backup" : user.trim();
        sink.accept(event(sourceIp, sourcePort, Action.LOGIN_ATTEMPT,
                user.trim() + ":" + (pass == null ? "" : pass.trim())));
        write(out, "\r\nLast login: Mon Nov  2 02:00:13 2026 from 10.10.0.5\r\n");

        String line;
        int commands = 0;
        while (commands++ < MAX_FAKE_COMMANDS) {
            write(out, name + "@nas01:~$ ");
            if ((line = in.readLine()) == null) {
                return;
            }
            String command = line.trim();
            if (command.equals("exit") || command.equals("logout")) {
                write(out, "logout\r\n");
                return;
            }
            write(out, fakeShell(command, name));
        }
    }

    private static String fakeShell(String command, String user) {
        if (command.isEmpty()) {
            return "";
        }
        if (command.equals("whoami")) {
            return user + "\r\n";
        }
        if (command.equals("pwd")) {
            return "/home/" + user + "\r\n";
        }
        if (command.equals("ls") || command.startsWith("ls ")) {
            return String.join("  ", lureFiles().keySet()) + "\r\n";
        }
        if (command.startsWith("cat ")) {
            String path = command.substring(4).trim();
            String content = lureFiles().get(path);
            return content == null ? "cat: " + path + ": No such file or directory\r\n"
                    : content.replace("\n", "\r\n");
        }
        String word = command.split("\\s+", 2)[0];
        return "-sh: " + word + ": command not found\r\n";
    }

    private static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
