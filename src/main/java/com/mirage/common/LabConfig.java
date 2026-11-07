package com.mirage.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * One place for the settings a two-laptop demo needs: the lab CIDR range (the
 * {@link ScopeGuard} boundary), the Console host and port, the decoy ports, and
 * the Specter counter-watch port.
 *
 * <p>Values come from, in increasing priority: built-in defaults, an optional
 * {@code mirage.properties} in the working directory, then {@code --key=value}
 * command-line flags. The defaults reproduce today's single-machine behaviour, so
 * nothing new has to be configured to run on one laptop.
 *
 * <p>{@link #scopeGuard()} validates the range through
 * {@link ScopeGuard#requireLabRange(List)} before building a guard, so an unsafe
 * (public or over-wide) range stops the app at start-up.
 */
public final class LabConfig {

    /** Property keys (also the stem of the CLI flags, e.g. {@code lab.cidrs} -> {@code --lab-cidr}). */
    public static final String DEFAULT_CIDRS = "10.10.0.0/24,127.0.0.1/32";

    private final List<String> labCidrs;
    private final String consoleHost;
    private final int consolePort;
    private final int httpDecoyPort;
    private final int ftpDecoyPort;
    private final int telnetDecoyPort;
    private final int counterWatchPort;

    private LabConfig(Map<String, String> v) {
        this.labCidrs = splitCidrs(v.getOrDefault("lab.cidrs", DEFAULT_CIDRS));
        this.consoleHost = v.getOrDefault("console.host", "127.0.0.1");
        this.consolePort = intValue(v, "console.port", 8443);
        this.httpDecoyPort = intValue(v, "decoy.http.port", 8081);
        this.ftpDecoyPort = intValue(v, "decoy.ftp.port", 8021);
        this.telnetDecoyPort = intValue(v, "decoy.telnet.port", 8023);
        this.counterWatchPort = intValue(v, "counterwatch.port", 8080);
    }

    /** The built-in defaults (single-machine demo on 127.0.0.1). */
    public static LabConfig defaults() {
        return new LabConfig(Map.of());
    }

    /** Core, test-friendly builder: everything already resolved into a key/value map. */
    public static LabConfig from(Map<String, String> values) {
        return new LabConfig(values);
    }

    /**
     * Load defaults, overlay {@code mirage.properties} if it exists next to the app, then
     * overlay {@code --key=value} / {@code --key value} command-line flags.
     */
    public static LabConfig load(Path propertiesFile, String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        if (propertiesFile != null && Files.isRegularFile(propertiesFile)) {
            Properties props = new Properties();
            try (var in = Files.newInputStream(propertiesFile)) {
                props.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException("could not read " + propertiesFile, e);
            }
            for (String name : props.stringPropertyNames()) {
                values.put(name, props.getProperty(name).trim());
            }
        }
        values.putAll(parseArgs(args));
        return new LabConfig(values);
    }

    /** Convenience: load {@code ./mirage.properties} (if present) plus CLI flags. */
    public static LabConfig load(String[] args) {
        return load(Path.of("mirage.properties"), args);
    }

    /** Map the small set of supported CLI flags onto property keys. */
    public static Map<String, String> parseArgs(String[] args) {
        Map<String, String> flagToKey = Map.of(
                "--lab-cidr", "lab.cidrs",
                "--console-host", "console.host",
                "--console-port", "console.port",
                "--http-port", "decoy.http.port",
                "--ftp-port", "decoy.ftp.port",
                "--telnet-port", "decoy.telnet.port",
                "--counterwatch-port", "counterwatch.port");
        Map<String, String> out = new LinkedHashMap<>();
        if (args == null) {
            return out;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null || !arg.startsWith("--")) {
                continue;
            }
            String flag;
            String value;
            int eq = arg.indexOf('=');
            if (eq >= 0) {
                flag = arg.substring(0, eq);
                value = arg.substring(eq + 1);
            } else {
                flag = arg;
                value = (i + 1 < args.length && !args[i + 1].startsWith("--")) ? args[++i] : "";
            }
            String key = flagToKey.get(flag);
            if (key != null && !value.isBlank()) {
                out.put(key, value.trim());
            }
        }
        return out;
    }

    /** Validate the configured range, then build the {@link ScopeGuard} from it. */
    public ScopeGuard scopeGuard() {
        ScopeGuard.requireLabRange(labCidrs);
        return new ScopeGuard(labCidrs);
    }

    public List<String> labCidrs() {
        return labCidrs;
    }

    public String consoleHost() {
        return consoleHost;
    }

    public int consolePort() {
        return consolePort;
    }

    public int httpDecoyPort() {
        return httpDecoyPort;
    }

    public int ftpDecoyPort() {
        return ftpDecoyPort;
    }

    public int telnetDecoyPort() {
        return telnetDecoyPort;
    }

    public int counterWatchPort() {
        return counterWatchPort;
    }

    /** A one-line summary for logs and the dashboard status bar. */
    public String summary() {
        return "lab=" + labCidrs + " console=" + consoleHost + ":" + consolePort
                + " decoys[http=" + httpDecoyPort + ",ftp=" + ftpDecoyPort
                + ",telnet=" + telnetDecoyPort + "] counterwatch=" + counterWatchPort;
    }

    /**
     * Best-guess site-local IPv4 of this machine, for telling the operator what the attacker
     * laptop should target. Returns empty if only loopback is available.
     */
    public static Optional<String> primaryLanIp() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr.isSiteLocalAddress() && addr.getAddress().length == 4) {
                        return Optional.of(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            // fall through to empty
        }
        return Optional.empty();
    }

    private static List<String> splitCidrs(String raw) {
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return List.copyOf(out);
    }

    private static int intValue(Map<String, String> v, String key, int fallback) {
        String raw = v.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " is not a number: " + raw);
        }
    }
}
