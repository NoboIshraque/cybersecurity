package com.mirage.specter;

import com.mirage.common.LabConfig;

import java.util.List;

/**
 * The campaigns the Specter console offers, built from the {@link LabConfig} (B-21) and kept
 * out of the Swing class so they can be tested.
 *
 * <p>The recon scan probes exactly the configured decoy ports. It never includes the
 * counter-watch port on a one-laptop demo (there, two listeners can't share a port), so
 * Specter's own recon no longer finds and trips its own counter-watch.
 */
public final class Campaigns {

    /** The choices in the Specter console, in menu order. */
    public static final String[] CHOICES = {
            "Recon scan", "Brute force", "Password spray", "Honeytoken replay", "Full campaign"
    };

    private Campaigns() {
    }

    public static Campaign forChoice(String choice, LabConfig config) {
        return switch (choice) {
            case "Recon scan" -> recon(config);
            case "Brute force" -> new Campaign("brute", List.of(brute()));
            case "Password spray" -> new Campaign("spray", List.of(spray()));
            case "Honeytoken replay" -> new Campaign("honeytoken", List.of(replay()));
            default -> new Campaign("full", List.of(scan(config), brute(), spray(), replay()));
        };
    }

    public static Campaign recon(LabConfig config) {
        return new Campaign("recon", List.of(scan(config)));
    }

    private static PortScanAttack scan(LabConfig config) {
        return new PortScanAttack(List.of(config.httpDecoyPort(), config.ftpDecoyPort(), config.telnetDecoyPort()));
    }

    private static AttackStrategy brute() {
        return new BruteForceAttack(List.of("123456", "password", "admin", "letmein", "root"));
    }

    private static AttackStrategy spray() {
        return new PasswordSprayAttack(List.of("admin", "root", "user", "test"), "Password123!");
    }

    private static AttackStrategy replay() {
        return new HoneytokenReplayAttack("svc_backup:S3cr3tBackup!");
    }
}
