package com.mirage;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A-09: no key material is committed, and this keeps it that way. Lab keystores (certs/lab.p12),
 * the ledger key (data/ledger-key.pem) and the bait lures (bait/vpn.key) are made on each machine
 * and git-ignored; if one is ever added by force, this test (run by CI's "mvn verify") fails.
 * Skipped where there is no git checkout (e.g. a source zip).
 */
class NoSecretsCommittedTest {

    private static final List<String> KEY_FILES = List.of(".jks", ".p12", ".pfx", ".pem", ".key", ".keystore");

    @Test
    void noKeystoreOrPrivateKeyIsTracked() throws Exception {
        List<String> tracked;
        try {
            Process git = new ProcessBuilder("git", "ls-files").redirectErrorStream(true).start();
            try (BufferedReader out = new BufferedReader(new InputStreamReader(git.getInputStream(), StandardCharsets.UTF_8))) {
                tracked = out.lines().toList();
            }
            Assumptions.assumeTrue(git.waitFor(30, TimeUnit.SECONDS) && git.exitValue() == 0, "not a git checkout");
        } catch (java.io.IOException e) {
            Assumptions.abort("git is not available: " + e.getMessage());
            return;
        }
        List<String> leaked = tracked.stream()
                .filter(f -> KEY_FILES.stream().anyMatch(ext -> f.toLowerCase(Locale.ROOT).endsWith(ext)))
                .toList();
        assertEquals(List.of(), leaked, "key material is committed; remove it with git rm --cached and rotate it");
    }
}
