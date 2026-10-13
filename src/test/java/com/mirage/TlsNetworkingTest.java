package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.TlsFactories;
import com.mirage.console.ConsoleServer;
import com.mirage.console.SensorRegistry;
import com.mirage.sensor.SensorClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** The Sensor-to-Console event flow works over a real TLS link. */
class TlsNetworkingTest {

    @TempDir
    Path tmp;

    @Test
    void eventFlowsOverTls() throws Exception {
        Path keystore = tmp.resolve("lab.p12");
        char[] password = "changeit".toCharArray();
        Assumptions.assumeTrue(generateKeystore(keystore), "keytool must be available to run this test");

        BlockingQueue<AttackEvent> queue = new ArrayBlockingQueue<>(16);
        SensorRegistry registry = new SensorRegistry();
        ConsoleServer server = new ConsoleServer(
                TlsFactories.serverFactory(keystore, password), queue, registry);
        int port = server.start(0);

        try (SensorClient client = new SensorClient("sensor-a",
                TlsFactories.clientFactoryTrustingKeystore(keystore, password), "127.0.0.1", port)) {
            client.sendHeartbeat();
            client.sendEvent(new AttackEvent("10.10.0.5", 5555, "http-login",
                    Action.CONNECT, "", Instant.now()));

            AttackEvent received = queue.poll(3, TimeUnit.SECONDS);
            assertNotNull(received, "the event should traverse the TLS link");
            assertEquals("10.10.0.5", received.sourceIp());
        } finally {
            server.stop();
        }
    }

    private boolean generateKeystore(Path keystore) {
        try {
            Process p = new ProcessBuilder("keytool", "-genkeypair", "-alias", "mirage",
                    "-keyalg", "RSA", "-keysize", "2048", "-storetype", "PKCS12",
                    "-keystore", keystore.toString(), "-storepass", "changeit",
                    "-keypass", "changeit", "-dname", "CN=localhost", "-validity", "365")
                    .redirectErrorStream(true).start();
            boolean finished = p.waitFor(30, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0 && Files.exists(keystore);
        } catch (Exception e) {
            return false;
        }
    }
}
