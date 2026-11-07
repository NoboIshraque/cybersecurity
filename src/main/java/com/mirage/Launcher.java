package com.mirage;

import com.mirage.common.LabConfig;
import com.mirage.console.ConsoleMain;
import com.mirage.sensor.SensorMain;
import com.mirage.specter.SpecterMain;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The one entry point of the runnable jar ({@code java -jar mirage.jar <role> [--flags]}).
 *
 * <ul>
 *   <li>{@code defender} — Laptop A, the protected host: the Console and a local Sensor
 *       (the decoys) in one process. The Sensor always reports to this machine's Console.</li>
 *   <li>{@code attacker} — Laptop B: Specter plus its passive counter-watch listener.</li>
 *   <li>{@code console}, {@code sensor}, {@code specter} — one app on its own, as before.</li>
 * </ul>
 *
 * Every flag after the role is passed through ({@code --lab-cidr}, {@code --console-host}, …,
 * see {@link LabConfig}); a {@code mirage.properties} in the working directory is read too.
 * Safety is unchanged: every role builds its {@code ScopeGuard} from the validated lab range.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }
        String role = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (role) {
            case "defender" -> defender(rest);
            case "attacker", "specter" -> SpecterMain.main(rest);
            case "console" -> ConsoleMain.main(rest);
            case "sensor" -> SensorMain.main(rest);
            case "help", "-h", "--help" -> usage();
            default -> {
                System.err.println("Unknown role: " + args[0]);
                usage();
                System.exit(2);
            }
        }
    }

    /** Console on a background thread, then (once it is listening) the Sensor on this one. */
    private static void defender(String[] args) throws Exception {
        LabConfig config = LabConfig.load(args);
        Thread console = new Thread(() -> {
            try {
                ConsoleMain.main(args);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("Console stopped: " + e);
            }
        }, "console-main");
        console.start();

        if (!waitForPort(config.consolePort(), 20_000, console)) {
            System.err.println("Console did not come up on port " + config.consolePort()
                    + " (see the messages above); not starting the Sensor.");
            System.exit(1);
        }
        SensorMain.main(sensorArgs(args));
    }

    /**
     * The defender's Sensor sits next to its Console, so it always reports to 127.0.0.1,
     * whatever {@code console.host} says (on a shared config that is the defender's LAN IP,
     * which Specter on the attacker laptop uses as its target).
     */
    static String[] sensorArgs(String[] args) {
        List<String> out = new ArrayList<>();
        out.add("--console-host=127.0.0.1"); // first, so a bare first arg can never be read as a host
        out.addAll(Arrays.asList(args));
        out.add("--console-host=127.0.0.1"); // last flag wins over anything the user passed
        return out.toArray(String[]::new);
    }

    private static boolean waitForPort(int port, long timeoutMillis, Thread owner) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (!owner.isAlive()) {
                return false; // the Console refused to start (e.g. an unsafe lab range)
            }
            try (Socket probe = new Socket()) {
                probe.connect(new InetSocketAddress("127.0.0.1", port), 300);
                return true;
            } catch (IOException notYet) {
                Thread.sleep(250);
            }
        }
        return false;
    }

    private static void usage() {
        System.out.println("""
                Mirage - deception-based IDS (lab use only)

                usage: java -jar mirage.jar <role> [--flags]

                roles:
                  defender   Laptop A: Console + Sensor (decoys) in one process
                  attacker   Laptop B: Specter + counter-watch
                  console    the Console alone
                  sensor     a Sensor alone (reports to --console-host)
                  specter    Specter alone (same as attacker)

                flags (or put them in mirage.properties next to the jar):
                  --lab-cidr 192.168.0.0/24,127.0.0.1/32   lab range (private/loopback, /16 or narrower)
                  --console-host 192.168.0.107             the defender laptop's IP (Specter's target)
                  --console-port 8443   --http-port 8081   --ftp-port 8021
                  --telnet-port 8023    --counterwatch-port 8080

                Every outbound action is checked by ScopeGuard against the lab range.""");
    }
}
