package com.mirage.specter;

import com.mirage.common.trace.TraceLevel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Probes a set of ports on the target, touching whichever decoys are listening. */
public final class PortScanAttack implements AttackStrategy {

    private final List<Integer> ports;

    /** Every port from {@code startPort} to {@code endPort}, inclusive. */
    public PortScanAttack(int startPort, int endPort) {
        this(IntStream.rangeClosed(startPort, endPort).boxed().toList());
    }

    /** Exactly these ports (sorted, duplicates dropped). B-21: built from the configured ports. */
    public PortScanAttack(Collection<Integer> ports) {
        List<Integer> sorted = ports.stream().distinct().sorted().toList();
        if (sorted.isEmpty() || sorted.get(0) < 1 || sorted.get(sorted.size() - 1) > 65535) {
            throw new IllegalArgumentException("ports must be 1-65535 and not empty: " + ports);
        }
        this.ports = sorted;
    }

    /** The ports this scan probes, in order. */
    public List<Integer> ports() {
        return ports;
    }

    @Override
    public void run(Target target, AttackTrace trace) {
        String spec = spec();
        trace.line(TraceLevel.COMMAND, "nmap -p " + spec + " " + target.host());
        int open = 0;
        for (int port : ports) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(target.host(), port), 200);
                open++;
                trace.line(TraceLevel.GOOD, "port " + port + "/tcp  open");
            } catch (IOException ignored) {
                // closed port; keep scanning (not logged, to keep the sweep readable)
            }
        }
        trace.line(open > 0 ? TraceLevel.GOOD : TraceLevel.WARN,
                "scan complete: " + open + " open port(s) in " + spec);
        trace.line(TraceLevel.BANNER, "SCAN COMPLETE — " + open + " OPEN");
    }

    /** nmap-style: {@code 8020-8085} for a contiguous range, else {@code 8021,8023,8081}. */
    private String spec() {
        int first = ports.get(0);
        int last = ports.get(ports.size() - 1);
        if (ports.size() > 2 && last - first + 1 == ports.size()) {
            return first + "-" + last;
        }
        return ports.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    @Override
    public String name() {
        return "port-scan";
    }
}
