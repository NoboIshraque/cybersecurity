package com.mirage.specter;

import com.mirage.common.trace.TraceLevel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Sweeps a range of ports on the target, touching whichever decoys are listening. */
public final class PortScanAttack implements AttackStrategy {

    private final int startPort;
    private final int endPort;

    public PortScanAttack(int startPort, int endPort) {
        this.startPort = startPort;
        this.endPort = endPort;
    }

    @Override
    public void run(Target target, AttackTrace trace) {
        trace.line(TraceLevel.COMMAND, "nmap -p " + startPort + "-" + endPort + " " + target.host());
        int open = 0;
        for (int port = startPort; port <= endPort; port++) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(target.host(), port), 200);
                open++;
                trace.line(TraceLevel.GOOD, "port " + port + "/tcp  open");
            } catch (IOException ignored) {
                // closed port; keep scanning (not logged, to keep the sweep readable)
            }
        }
        trace.line(open > 0 ? TraceLevel.GOOD : TraceLevel.WARN,
                "scan complete: " + open + " open port(s) in " + startPort + "-" + endPort);
        trace.line(TraceLevel.BANNER, "SCAN COMPLETE — " + open + " OPEN");
    }

    @Override
    public String name() {
        return "port-scan";
    }
}
