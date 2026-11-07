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
import java.util.function.Predicate;
import java.util.regex.Pattern;

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
    private final Path baitDir;
    /** Explicit Sensor id ({@code sensor.id} / {@code --sensor-id}); null means derive one. */
    private final String sensorId;

    private LabConfig(Map<String, String> v) {
        this.labCidrs = splitCidrs(v.getOrDefault("lab.cidrs", DEFAULT_CIDRS));
        this.consoleHost = v.getOrDefault("console.host", "127.0.0.1");
        this.consolePort = portValue(v, "console.port", 8443);
        this.httpDecoyPort = portValue(v, "decoy.http.port", 8081);
        this.ftpDecoyPort = portValue(v, "decoy.ftp.port", 8021);
        this.telnetDecoyPort = portValue(v, "decoy.telnet.port", 8023);
        this.counterWatchPort = portValue(v, "counterwatch.port", 8080);
        this.baitDir = Path.of(v.getOrDefault("bait.dir", "bait"));
        String id = v.get("sensor.id");
        this.sensorId = id == null || id.isBlank() ? null : id.trim();
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
                values.put(name, stripInlineComment(props.getProperty(name)));
            }
        }
        values.putAll(parseArgs(args));
        return new LabConfig(values);
    }

    /** Convenience: load {@code ./mirage.properties} (if present) plus CLI flags. */
    public static LabConfig load(String[] args) {
        return load(Path.of("mirage.properties"), args);
    }

    /** The supported CLI flags and their property keys, in the order the usage lists them. */
    private static final Map<String, String> FLAGS = flags();

    private static Map<String, String> flags() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("--lab-cidr", "lab.cidrs");
        f.put("--lab-cidrs", "lab.cidrs"); // B-32: the natural spelling, matching the property name
        f.put("--console-host", "console.host");
        f.put("--console-port", "console.port");
        f.put("--http-port", "decoy.http.port");
        f.put("--ftp-port", "decoy.ftp.port");
        f.put("--telnet-port", "decoy.telnet.port");
        f.put("--counterwatch-port", "counterwatch.port");
        f.put("--bait-dir", "bait.dir");
        f.put("--sensor-id", "sensor.id");
        return java.util.Collections.unmodifiableMap(f);
    }

    /**
     * Map the supported CLI flags onto property keys. B-32: an unknown flag or a flag without a
     * value is refused (it used to be dropped silently, so a typo ran on the defaults). A bare
     * word that is not a flag's value is ignored, as before (the first one may be a host).
     */
    public static Map<String, String> parseArgs(String[] args) {
        Map<String, String> flagToKey = FLAGS;
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
            if (key == null) {
                throw new IllegalArgumentException("unknown option " + flag + " (options: "
                        + String.join(", ", FLAGS.keySet()) + ")");
            }
            if (value.isBlank()) {
                throw new IllegalArgumentException(flag + " needs a value, e.g. " + flag + "=...");
            }
            out.put(key, value.trim());
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

    /** The Sensor's bait folder (default {@code bait} in the working directory). */
    public Path baitDir() {
        return baitDir;
    }

    /**
     * This Sensor's id: {@code sensor.id} if set, else {@code sensor-<host>-<http decoy port>}
     * (B-11). Two Sensors on one machine must use different decoy ports, so the pair is unique
     * there, and it stays the same across restarts.
     */
    public String sensorId(String hostname) {
        return sensorId != null ? sensorId : "sensor-" + hostname + "-" + httpDecoyPort;
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
        return primaryLanIp(ip -> true);
    }

    /**
     * Like {@link #primaryLanIp()}, but prefers an address that {@code preferred} accepts — the
     * Console passes its ScopeGuard, so a machine with a VirtualBox/VPN adapter still reports the
     * address on the lab network rather than the first adapter it happens to find.
     */
    public static Optional<String> primaryLanIp(Predicate<String> preferred) {
        return pickLanIp(siteLocalIpv4s(), preferred);
    }

    /** The first candidate {@code preferred} accepts, else the first candidate. */
    public static Optional<String> pickLanIp(List<String> candidates, Predicate<String> preferred) {
        for (String ip : candidates) {
            if (preferred.test(ip)) {
                return Optional.of(ip);
            }
        }
        return candidates.stream().findFirst();
    }

    private static List<String> siteLocalIpv4s() {
        List<String> out = new ArrayList<>();
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
                        out.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            // fall through with what was found
        }
        return out;
    }

    /**
     * {@code .properties} files have no inline comments: Java keeps {@code "1.2.3.4   # note"}
     * as the whole value. People write them anyway (and none of our values contain {@code #}),
     * so drop a whitespace-preceded {@code #...} tail and trim.
     */
    static String stripInlineComment(String raw) {
        return INLINE_COMMENT.matcher(raw).replaceFirst("").trim();
    }

    private static final Pattern INLINE_COMMENT = Pattern.compile("\\s+#.*$");

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

    /** A TCP port setting: refused, naming the key, unless it is 1-65535 (B-32). */
    private static int portValue(Map<String, String> v, String key, int fallback) {
        int port = intValue(v, key, fallback);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(key + " must be a port from 1 to 65535, not " + port);
        }
        return port;
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
