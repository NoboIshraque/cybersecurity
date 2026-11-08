package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.LabConfig;
import com.mirage.common.LinkSecurity;
import com.mirage.common.TlsFactories;
import com.mirage.console.ConsoleServer;
import com.mirage.console.SensorRegistry;
import com.mirage.sensor.SensorClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A-02 / A-03, the agreed policy: without a lab keystore the Console–Sensor link is plain but
 * never leaves the machine (the Console binds loopback, a Sensor refuses a remote Console);
 * with one it is mutual TLS pinned to the lab certificate. A broken TLS set-up refuses to
 * start instead of falling back to plain text, and there is no default password.
 */
class LinkSecurityTest {

    private static final String PASSWORD = "lab-test-pass";

    @TempDir
    Path tmp;

    // ---- no keystore: plain, loopback only ----------------------------------------------

    @Test
    void withoutAKeystoreTheConsoleListensOnLoopbackOnly() throws Exception {
        LinkSecurity plain = LinkSecurity.resolve(null, null);
        assertFalse(plain.isTls());
        assertNotNull(plain.bindAddress(), "plain text must not listen on every interface");
        assertTrue(plain.bindAddress().isLoopbackAddress());

        // Over a real socket: reachable on 127.0.0.1, not on this machine's LAN address.
        Optional<String> lan = LabConfig.primaryLanIp();
        ConsoleServer server = new ConsoleServer(plain.serverFactory(), new LinkedBlockingQueue<>(),
                new SensorRegistry()).setBindAddress(plain.bindAddress());
        int port = server.start(0);
        try {
            new Socket("127.0.0.1", port).close();
            if (lan.isPresent()) {
                assertThrows(IOException.class, () -> {
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(lan.get(), port), 1000);
                    }
                }, "a host on the LAN (" + lan.get() + ") must not reach a plain-text Console");
            }
        } finally {
            server.stop();
        }
    }

    @Test
    void withoutAKeystoreASensorRefusesARemoteConsole() {
        LinkSecurity plain = LinkSecurity.resolve(null, null);
        assertDoesNotThrow(() -> plain.clientFactory("127.0.0.1"));
        assertDoesNotThrow(() -> plain.clientFactory("localhost"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> plain.clientFactory("192.168.0.107"));
        assertTrue(e.getMessage().contains("TLS"), e.getMessage());
    }

    // ---- a broken TLS set-up fails closed -------------------------------------------------

    @Test
    void aKeystoreWithoutAPasswordRefusesToStart() throws Exception {
        Path lab = keystore("lab");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LinkSecurity.resolve(lab.toString(), null), "no 'changeit' default");
        assertTrue(e.getMessage().contains(LinkSecurity.PASSWORD_ENV), e.getMessage());
    }

    @Test
    void aKeystoreThatCannotBeLoadedRefusesToStart() throws Exception {
        Path lab = keystore("lab");
        assertThrows(IllegalArgumentException.class, () -> LinkSecurity.resolve(lab.toString(), "wrong"),
                "a wrong password must not fall back to plain text");
        assertThrows(IllegalArgumentException.class,
                () -> LinkSecurity.resolve(tmp.resolve("missing.p12").toString(), PASSWORD));
    }

    // ---- with the lab keystore: mutual TLS, pinned ---------------------------------------

    @Test
    void sensorAndConsoleWithTheLabKeystoreTalkOverMutualTls() throws Exception {
        LinkSecurity lab = LinkSecurity.resolve(keystore("lab").toString(), PASSWORD);
        assertTrue(lab.isTls());
        BlockingQueue<AttackEvent> queue = new LinkedBlockingQueue<>();
        ConsoleServer server = new ConsoleServer(lab.serverFactory(), queue, new SensorRegistry())
                .setBindAddress(lab.bindAddress());
        int port = server.start(0);
        try (SensorClient sensor = new SensorClient("s1", lab.clientFactory("127.0.0.1"), "127.0.0.1", port)) {
            sensor.sendEvent(new AttackEvent("10.10.0.5", 1, "ftp", Action.CONNECT, "", Instant.now()));
            assertNotNull(queue.poll(5, TimeUnit.SECONDS), "the event crosses the mutual-TLS link");
        } finally {
            server.stop();
        }
    }

    /** The Console used to accept any TLS client: only the server proved who it was. */
    @Test
    void theConsoleRejectsASensorWithoutTheLabCertificate() throws Exception {
        Path labStore = keystore("lab");
        LinkSecurity lab = LinkSecurity.resolve(labStore.toString(), PASSWORD);
        BlockingQueue<AttackEvent> queue = new LinkedBlockingQueue<>();
        ConsoleServer server = new ConsoleServer(lab.serverFactory(), queue, new SensorRegistry())
                .setBindAddress(lab.bindAddress());
        int port = server.start(0);
        // trusts the Console's certificate, but has no client certificate of its own
        try (SensorClient stranger = new SensorClient("stranger",
                TlsFactories.clientFactoryTrustingKeystore(labStore, PASSWORD.toCharArray()), "127.0.0.1", port)) {
            try {
                stranger.sendEvent(new AttackEvent("10.10.0.66", 1, "ftp", Action.DECOY_ACCESS, "fake",
                        Instant.now()));
            } catch (IOException expected) {
                // the handshake may already fail on the write
            }
            assertNull(queue.poll(1, TimeUnit.SECONDS), "an event from a Sensor without the lab cert is never queued");
        } finally {
            server.stop();
        }
    }

    /** A-03: the Sensor used to obey whoever answered on the Console's address. */
    @Test
    void aSensorRefusesAnImpostorConsole() throws Exception {
        LinkSecurity lab = LinkSecurity.resolve(keystore("lab").toString(), PASSWORD);
        Path otherStore = keystore("impostor");
        SSLServerSocketFactory impostorTls = TlsFactories.serverFactory(otherStore, PASSWORD.toCharArray());
        for (ServerSocketFactory impostor : new ServerSocketFactory[]{impostorTls, ServerSocketFactory.getDefault()}) {
            try (ServerSocket fake = impostor.createServerSocket(0)) {
                CompletableFuture.runAsync(() -> {
                    try (Socket s = fake.accept()) {
                        s.getOutputStream().write("{\"type\":\"UNBLOCK\",\"targetIp\":\"10.10.0.66\"}\n".getBytes());
                        s.getOutputStream().flush();
                        Thread.sleep(500);
                    } catch (Exception ignored) {
                        // the Sensor hung up on us
                    }
                });
                try (SensorClient sensor = new SensorClient("s1", lab.clientFactory("127.0.0.1"), "127.0.0.1",
                        fake.getLocalPort())) {
                    assertThrows(IOException.class, () -> {
                        sensor.sendHeartbeat();
                        sensor.receive(c -> fail("obeyed an impostor: " + c));
                    }, "the TLS handshake must fail against " + impostor.getClass().getSimpleName());
                }
            }
        }
    }

    private Path keystore(String name) throws Exception {
        Path store = tmp.resolve(name + ".p12");
        if (Files.exists(store)) {
            return store;
        }
        Process p = new ProcessBuilder("keytool", "-genkeypair", "-alias", name, "-keyalg", "RSA",
                "-keysize", "2048", "-storetype", "PKCS12", "-keystore", store.toString(),
                "-storepass", PASSWORD, "-keypass", PASSWORD, "-dname", "CN=mirage-" + name, "-validity", "2")
                .redirectErrorStream(true).start();
        boolean done = p.waitFor(30, TimeUnit.SECONDS);
        Assumptions.assumeTrue(done && p.exitValue() == 0 && Files.exists(store), "keytool is needed for this test");
        return store;
    }
}
