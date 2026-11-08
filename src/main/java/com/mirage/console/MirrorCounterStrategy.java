package com.mirage.console;

import com.mirage.common.Classification;
import com.mirage.common.LabConfig;
import com.mirage.specter.BruteForceAttack;
import com.mirage.specter.Campaign;
import com.mirage.specter.HoneytokenReplayAttack;
import com.mirage.specter.PasswordSprayAttack;
import com.mirage.specter.PortScanAttack;

import java.util.List;
import java.util.Optional;

/**
 * Mirrors the attacker's own method back at them: the detected MITRE ATT&CK
 * technique picks the matching Specter campaign, with a port scan as the fallback
 * "other method" when a technique has no direct mirror. This class only chooses a
 * campaign; it never touches the network. {@link CounterOffensive} runs the chosen
 * campaign through {@link com.mirage.specter.Attacker}, which checks ScopeGuard
 * first, so a counter can never reach a host outside the lab range.
 *
 * <p>The counter-scans probe only the configured counter-watch port (B-21): the one port
 * the lab knows is listening on the attacker's side, and the narrowest counter that still
 * reaches it, whatever the ports are set to.
 */
public final class MirrorCounterStrategy implements CounterStrategy {

    private final int counterWatchPort;

    /** With the default counter-watch port. */
    public MirrorCounterStrategy() {
        this(LabConfig.defaults().counterWatchPort());
    }

    public MirrorCounterStrategy(int counterWatchPort) {
        this.counterWatchPort = counterWatchPort;
    }

    @Override
    public Optional<Campaign> counterFor(Classification technique) {
        return Optional.of(switch (technique.attackTechnique()) {
            case "T1046" -> new Campaign("mirror-scan", List.of(scan()));
            case "T1110" -> new Campaign("mirror-brute", List.of(
                    new BruteForceAttack(List.of("123456", "password", "admin", "letmein", "root"))));
            case "T1110.003" -> new Campaign("mirror-spray", List.of(
                    new PasswordSprayAttack(List.of("admin", "root", "user", "test"), "Password123!")));
            case "T1078" -> new Campaign("mirror-replay", List.of(
                    new HoneytokenReplayAttack("svc_backup:S3cr3tBackup!")));
            // T1021 lateral movement, T1485 bait/ransomware, and anything else:
            // no like-for-like reply, so fall back to probing the source's counter-watch.
            default -> new Campaign("counter-probe", List.of(scan()));
        });
    }

    @Override
    public Campaign fullCounter() {
        return new Campaign("full-counter", List.of(
                scan(),
                new BruteForceAttack(List.of("123456", "password", "admin", "letmein", "root")),
                new PasswordSprayAttack(List.of("admin", "root", "user", "test"), "Password123!"),
                new HoneytokenReplayAttack("svc_backup:S3cr3tBackup!")));
    }

    private PortScanAttack scan() {
        return new PortScanAttack(List.of(counterWatchPort));
    }
}
