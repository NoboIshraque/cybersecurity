package com.mirage.common;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Enumeration;

/**
 * Builds TLS socket factories for the Console (server) and the Sensor (client)
 * from a lab keystore. Enabling TLS is just a factory swap: {@code ConsoleServer}
 * takes a {@link javax.net.ServerSocketFactory} and {@code SensorClient} takes a
 * {@link javax.net.SocketFactory}, and the SSL variants returned here fit both.
 *
 * <p>Generate the lab keystore once (see {@code certs/README.md}):
 * {@code keytool -genkeypair -alias mirage -keyalg RSA -keysize 2048
 * -storetype PKCS12 -keystore lab.p12 -validity 365}.
 */
public final class TlsFactories {

    private TlsFactories() {
    }

    /** Server side: presents the keystore's certificate and key. */
    public static SSLServerSocketFactory serverFactory(Path keystore, char[] password) {
        try {
            KeyStore ks = load(keystore, password);
            KeyManagerFactory kmf =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, password);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, null);
            return ctx.getServerSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("could not build TLS server factory", e);
        }
    }

    /** Client side: trusts the certificates in a truststore. */
    public static SSLSocketFactory clientFactory(Path truststore, char[] password) {
        try {
            return clientFactory(load(truststore, password));
        } catch (Exception e) {
            throw new IllegalStateException("could not build TLS client factory", e);
        }
    }

    /**
     * Client side for a self-signed lab setup: trusts the certificate(s) inside
     * the given keystore (handy when the team shares one keystore on a lab network).
     */
    public static SSLSocketFactory clientFactoryTrustingKeystore(Path keystore, char[] password) {
        try {
            KeyStore source = load(keystore, password);
            KeyStore trust = KeyStore.getInstance("PKCS12");
            trust.load(null, null);
            Enumeration<String> aliases = source.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = source.getCertificate(alias);
                if (cert != null) {
                    trust.setCertificateEntry(alias, cert);
                }
            }
            return clientFactory(trust);
        } catch (Exception e) {
            throw new IllegalStateException("could not build TLS client factory", e);
        }
    }

    private static SSLSocketFactory clientFactory(KeyStore truststore) throws Exception {
        TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(truststore);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx.getSocketFactory();
    }

    private static KeyStore load(Path path, char[] password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(path)) {
            ks.load(in, password);
        }
        return ks;
    }
}
