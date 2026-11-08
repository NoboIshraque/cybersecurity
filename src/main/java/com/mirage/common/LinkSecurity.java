package com.mirage.common;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.file.Path;

/**
 * How the Console–Sensor link is protected (A-02, A-03). One of two modes, never a silent
 * mix:
 *
 * <ul>
 *   <li><b>No lab keystore</b>: plain text, but the link never leaves the machine. The Console
 *       listens on 127.0.0.1 only, and a Sensor refuses a Console on another host. This is the
 *       {@code defender} role and the one-laptop demo, where Console and Sensor share a
 *       machine.</li>
 *   <li><b>Lab keystore</b> ({@code -Dmirage.keystore=...} plus the {@value #PASSWORD_ENV}
 *       environment variable): mutual TLS pinned to the lab certificate. Both ends present it
 *       and trust nothing else, so a stray client can't feed the Console events and an impostor
 *       can't give a Sensor orders. The Console then listens on every interface.</li>
 * </ul>
 *
 * A TLS set-up that is asked for but broken (no password, wrong password, missing file) refuses
 * to start instead of falling back to plain text, and there is no default password.
 */
public final class LinkSecurity {

    /** System property naming the lab keystore (PKCS12) on both the Console and the Sensor. */
    public static final String KEYSTORE_PROPERTY = "mirage.keystore";
    /** Environment variable holding the keystore password (not a -D flag: those show in process lists). */
    public static final String PASSWORD_ENV = "MIRAGE_TLS_PASSWORD";

    private static final String HOW = " (see certs/README.md)";

    private final SSLContext context; // null: plain, loopback only
    private final String keystore;

    private LinkSecurity(SSLContext context, String keystore) {
        this.context = context;
        this.keystore = keystore;
    }

    /** From {@code -Dmirage.keystore} and {@code MIRAGE_TLS_PASSWORD}; throws if the set-up is broken. */
    public static LinkSecurity fromEnvironment() {
        for (String old : new String[]{"mirage.keystore.password", "mirage.truststore", "mirage.truststore.password"}) {
            if (System.getProperty(old) != null) {
                throw new IllegalArgumentException("-D" + old + " is no longer used: give both apps the same lab "
                        + "keystore with -D" + KEYSTORE_PROPERTY + " and put its password in " + PASSWORD_ENV + HOW);
            }
        }
        return resolve(System.getProperty(KEYSTORE_PROPERTY), System.getenv(PASSWORD_ENV));
    }

    /**
     * {@code keystore == null}: plain text, loopback only. Otherwise mutual TLS from that keystore,
     * or {@link IllegalArgumentException} if it can't be used.
     */
    public static LinkSecurity resolve(String keystore, String password) {
        if (keystore == null || keystore.isBlank()) {
            return new LinkSecurity(null, null);
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("the TLS keystore " + keystore + " is set but " + PASSWORD_ENV
                    + " is not: set it to the keystore's password (there is no default)" + HOW);
        }
        try {
            return new LinkSecurity(TlsFactories.mutualContext(Path.of(keystore), password.toCharArray()), keystore);
        } catch (RuntimeException e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            throw new IllegalArgumentException("cannot use the TLS keystore " + keystore + " (" + root.getMessage()
                    + "); refusing to start rather than fall back to plain text" + HOW, e);
        }
    }

    public boolean isTls() {
        return context != null;
    }

    /** For the Console. With TLS, every Sensor must present the lab certificate. */
    public ServerSocketFactory serverFactory() {
        return context == null ? ServerSocketFactory.getDefault()
                : new ClientCertRequired(context.getServerSocketFactory());
    }

    /** Where the Console listens: 127.0.0.1 for plain text, every interface ({@code null}) with TLS. */
    public InetAddress bindAddress() {
        if (context != null) {
            return null;
        }
        try {
            return InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            return InetAddress.getLoopbackAddress();
        }
    }

    /** For a Sensor reporting to {@code consoleHost}; plain text is refused for a Console on another host. */
    public SocketFactory clientFactory(String consoleHost) {
        if (context != null) {
            return context.getSocketFactory();
        }
        if (!isLoopback(consoleHost)) {
            throw new IllegalArgumentException("the Console at " + consoleHost + " is not on this machine, so the "
                    + "link to it needs TLS: start both apps with -D" + KEYSTORE_PROPERTY + "=<lab keystore> and "
                    + PASSWORD_ENV + " set" + HOW);
        }
        return SocketFactory.getDefault();
    }

    /** One line for the start-up log. */
    public String describe() {
        return context == null ? "plain text, this machine only (127.0.0.1)"
                : "mutual TLS, pinned to the lab certificate in " + keystore;
    }

    private static boolean isLoopback(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** Server sockets that require the client's certificate (mutual TLS). */
    private static final class ClientCertRequired extends ServerSocketFactory {

        private final ServerSocketFactory delegate;

        ClientCertRequired(ServerSocketFactory delegate) {
            this.delegate = delegate;
        }

        private static ServerSocket require(ServerSocket s) {
            ((SSLServerSocket) s).setNeedClientAuth(true);
            return s;
        }

        @Override
        public ServerSocket createServerSocket() throws IOException {
            return require(delegate.createServerSocket());
        }

        @Override
        public ServerSocket createServerSocket(int port) throws IOException {
            return require(delegate.createServerSocket(port));
        }

        @Override
        public ServerSocket createServerSocket(int port, int backlog) throws IOException {
            return require(delegate.createServerSocket(port, backlog));
        }

        @Override
        public ServerSocket createServerSocket(int port, int backlog, InetAddress bind) throws IOException {
            return require(delegate.createServerSocket(port, backlog, bind));
        }
    }
}
