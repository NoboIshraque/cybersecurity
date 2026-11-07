package com.mirage.console;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Signs each ledger entry's hash with an elliptic-curve key pair, so the ledger
 * is not only tamper-evident (the hash chain) but also provably authentic: an
 * entry cannot be forged without the Console's private key.
 *
 * <p>A-06: {@link #loadOrCreate} keeps the key across restarts, so every stored signature stays
 * checkable. The private key is saved as PKCS#8 PEM ({@value #KEY_FILE}), encrypted
 * (PBES2, AES-256) when a password is given in {@value #PASSWORD_ENV}; the public key is
 * published as {@value #PUBLIC_KEY_FILE} so anyone can verify the ledger.
 */
public final class KeyPairSigner implements LedgerSigner {

    public static final String PASSWORD_ENV = "MIRAGE_LEDGER_PASSWORD";
    public static final String KEY_FILE = "ledger-key.pem";
    public static final String PUBLIC_KEY_FILE = "ledger-public.pem";

    private static final String ALGORITHM = "SHA256withECDSA";
    private static final String PBE = "PBEWithHmacSHA256AndAES_256";
    private static final int PBE_ITERATIONS = 200_000;

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    /** A fresh, unsaved key pair (tests and screenshot tools). */
    public KeyPairSigner() {
        this(generate());
    }

    private KeyPairSigner(KeyPair pair) {
        this.privateKey = pair.getPrivate();
        this.publicKey = pair.getPublic();
    }

    /**
     * The Console's ledger key from {@code dir}, created and saved on first use. With a
     * {@code password} the saved private key is encrypted (an unencrypted one is locked on the
     * spot); without one it is saved unencrypted. Throws {@link IllegalArgumentException} if the
     * saved key is locked and the password is missing or wrong: starting a new key instead
     * would silently break the chain.
     */
    public static KeyPairSigner loadOrCreate(Path dir, String password) {
        boolean locking = password != null && !password.isEmpty();
        Path keyFile = dir.resolve(KEY_FILE);
        Path publicFile = dir.resolve(PUBLIC_KEY_FILE);
        try {
            Files.createDirectories(dir);
            if (Files.notExists(keyFile)) {
                KeyPairSigner fresh = new KeyPairSigner(generate());
                fresh.save(keyFile, publicFile, locking ? password : null);
                return fresh;
            }
            if (Files.notExists(publicFile)) {
                throw new IllegalArgumentException(publicFile + " is missing, so the ledger key in " + keyFile
                        + " can't be checked; restore it (or move both away to start a new ledger)");
            }
            PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(
                    new X509EncodedKeySpec(pemBody(Files.readString(publicFile), "PUBLIC KEY")));
            String pem = Files.readString(keyFile);
            boolean locked = pem.contains("-----BEGIN ENCRYPTED PRIVATE KEY-----");
            PrivateKey privateKey;
            if (locked) {
                if (!locking) {
                    throw new IllegalArgumentException("the ledger key " + keyFile + " is password-protected: set "
                            + PASSWORD_ENV + " to its password");
                }
                privateKey = decrypt(pemBody(pem, "ENCRYPTED PRIVATE KEY"), password, keyFile);
            } else {
                privateKey = KeyFactory.getInstance("EC").generatePrivate(
                        new PKCS8EncodedKeySpec(pemBody(pem, "PRIVATE KEY")));
            }
            KeyPairSigner signer = new KeyPairSigner(new KeyPair(publicKey, privateKey));
            if (!signer.verify("ledger-key-check", signer.sign("ledger-key-check"))) {
                throw new IllegalArgumentException(keyFile + " and " + publicFile + " are not a key pair");
            }
            if (locking && !locked) {
                signer.save(keyFile, publicFile, password); // a password was added: lock the saved key now
            }
            return signer;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("could not load the ledger key from " + dir + ": " + e.getMessage(), e);
        }
    }

    /** Short fingerprint of the public key (first 16 hex chars of its SHA-256), stored with each alert. */
    public String keyId() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String sign(String data) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("signing failed", e);
        }
    }

    @Override
    public boolean verify(String data, String signatureBase64) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            return false;
        }
    }

    public PublicKey publicKey() {
        return publicKey;
    }

    private void save(Path keyFile, Path publicFile, String password) throws Exception {
        String key = password == null ? pem("PRIVATE KEY", privateKey.getEncoded())
                : pem("ENCRYPTED PRIVATE KEY", encrypt(privateKey.getEncoded(), password));
        Files.writeString(publicFile, pem("PUBLIC KEY", publicKey.getEncoded()));
        Files.writeString(keyFile, key);
    }

    private static byte[] encrypt(byte[] pkcs8, String password) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        byte[] iv = new byte[16];
        random.nextBytes(salt);
        random.nextBytes(iv);
        SecretKey key = SecretKeyFactory.getInstance(PBE).generateSecret(new PBEKeySpec(password.toCharArray()));
        Cipher cipher = Cipher.getInstance(PBE);
        cipher.init(Cipher.ENCRYPT_MODE, key, new PBEParameterSpec(salt, PBE_ITERATIONS, new IvParameterSpec(iv)));
        byte[] encrypted = cipher.doFinal(pkcs8);
        // EncryptedPrivateKeyInfo knows the scheme by its generic name, PBES2; the encoded
        // parameters still name the PRF (HMAC-SHA256) and the cipher (AES-256).
        AlgorithmParameters pbes2 = AlgorithmParameters.getInstance("PBES2");
        pbes2.init(cipher.getParameters().getEncoded());
        return new EncryptedPrivateKeyInfo(pbes2, encrypted).getEncoded();
    }

    private static PrivateKey decrypt(byte[] der, String password, Path from) {
        try {
            EncryptedPrivateKeyInfo info = new EncryptedPrivateKeyInfo(der);
            SecretKey key = SecretKeyFactory.getInstance(PBE).generateSecret(new PBEKeySpec(password.toCharArray()));
            Cipher cipher = Cipher.getInstance(PBE);
            cipher.init(Cipher.DECRYPT_MODE, key, info.getAlgParameters());
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(cipher.doFinal(info.getEncryptedData())));
        } catch (Exception e) {
            throw new IllegalArgumentException("could not unlock the ledger key " + from + ": is " + PASSWORD_ENV
                    + " the right password?", e);
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("could not create signing key pair", e);
        }
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }

    private static byte[] pemBody(String pem, String type) throws IOException {
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        int from = pem.indexOf(begin);
        int to = pem.indexOf(end);
        if (from < 0 || to < from) {
            throw new IOException("not a " + type + " PEM file");
        }
        return Base64.getMimeDecoder().decode(pem.substring(from + begin.length(), to));
    }
}
