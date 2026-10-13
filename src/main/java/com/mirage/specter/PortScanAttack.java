package com.mirage.specter;

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
    public void run(Target target) {
        for (int port = startPort; port <= endPort; port++) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(target.host(), port), 200);
            } catch (IOException ignored) {
                // closed port; keep scanning
            }
        }
    }

    @Override
    public String name() {
        return "port-scan";
    }
}
