package com.mirage.console;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * Signs each ledger entry's hash with an elliptic-curve key pair, so the ledger
 * is not only tamper-evident (the hash chain) but also provably authentic: an
 * entry cannot be forged without the Console's private key.
 */
public final class KeyPairSigner implements LedgerSigner {

    private static final String ALGORITHM = "SHA256withECDSA";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public KeyPairSigner() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            this.privateKey = pair.getPrivate();
            this.publicKey = pair.getPublic();
        } catch (Exception e) {
            throw new IllegalStateException("could not create signing key pair", e);
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
}
