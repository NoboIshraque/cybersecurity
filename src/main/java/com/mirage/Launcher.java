package com.mirage;

import com.mirage.common.LabConfig;
import com.mirage.console.ConsoleMain;
import com.mirage.sensor.SensorMain;
import com.mirage.specter.SpecterMain;

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

    /** One start-up step of a role; throws if that part of the role can't start. */
    @FunctionalInterface
    interface Step {
        void run(String[] args) throws Exception;
    }

    private static void defender(String[] args) throws Exception {
        int code = defender(args, ConsoleMain::start, SensorMain::start);
        if (code != 0) {
            System.exit(code); // also closes a dashboard that did open: the role is all or nothing
        }
        Thread.currentThread().join();
    }

    /**
     * Start the Console, and only once <em>it</em> is listening (its own bind succeeded, not
     * merely "something answers on the port"), the Sensor. If either part can't start, the whole
     * role fails with exit code 1 (B-10, B-31): no Sensor against a leftover Console, no
     * Console with a Sensor that has no decoys.
     */
    static int defender(String[] args, Step console, Step sensor) {
        try {
            console.run(args);
        } catch (Exception e) {
            System.err.println("Defender not started: " + e.getMessage());
            return 1;
        }
        try {
            sensor.run(sensorArgs(args));
        } catch (Exception e) {
            System.err.println("Defender stopped: the Sensor could not start: " + e.getMessage());
            return 1;
        }
        return 0;
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
                  --bait-dir bait       --sensor-id lab-a    (unknown options are refused)

                Every outbound action is checked by ScopeGuard against the lab range.""");
    }
}
