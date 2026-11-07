package com.mirage;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.CommandType;
import com.mirage.common.Honeytoken;
import com.mirage.common.ResponseCommand;
import com.mirage.common.ScopeGuard;
import com.mirage.console.AlertBus;
import com.mirage.console.AlertLedger;
import com.mirage.console.AttackerDirectory;
import com.mirage.console.AutoResponse;
import com.mirage.console.CorrelationEngine;
import com.mirage.console.HoneytokenStore;
import com.mirage.console.Persistence;
import com.mirage.console.ResponsePolicy;
import com.mirage.console.detection.BaitFileRule;
import com.mirage.console.detection.BruteForceRule;
import com.mirage.console.detection.DecoyAccessRule;
import com.mirage.console.detection.HoneytokenUseRule;
import com.mirage.console.detection.LateralMovementRule;
import com.mirage.console.detection.PasswordSprayRule;
import com.mirage.console.detection.PortScanRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-03: after the operator unblocks a source, it starts with a clean slate. Its earlier
 * findings stay in the ledger exactly once and are not raised again by its next packet; only
 * what it does after the unblock is judged, so a new break-in is still answered.
 * Runs the production rule set with auto-response on, as in DEMO step 6.
 */
class UnblockCleanSlateTest {

    private static final String SRC = "10.10.0.5";
    private static final String TOKEN = "svc_backup:S3cr3tBackup!";

    private final BlockingQueue<AttackEvent> queue = new LinkedBlockingQueue<>();
    private final List<ResponseCommand> sent = new CopyOnWriteArrayList<>();
    private final AlertLedger ledger = new AlertLedger();
    private final AttackerDirectory directory = new AttackerDirectory();
    private CorrelationEngine engine;

    @BeforeEach
    void wire() {
        HoneytokenStore honeytokens = new HoneytokenStore();
        honeytokens.plant(new Honeytoken("ht-1", TOKEN, "config/backup.cfg"));
        engine = new CorrelationEngine(queue,
                List.of(new PortScanRule(), new BruteForceRule(), new PasswordSprayRule(),
                        new HoneytokenUseRule(honeytokens), new DecoyAccessRule(), new BaitFileRule(),
                        new LateralMovementRule()),
                ledger, new AlertBus(),
                new ResponsePolicy(new ScopeGuard(List.of("10.10.0.0/24")), sent::add),
                Persistence.NONE, directory);
        engine.setAutoResponse(new AutoResponse(true));
        Thread t = new Thread(engine, "engine-test");
        t.setDaemon(true);
        t.start();
    }

    private void feed(String decoy, Action action, String payload) {
        queue.add(new AttackEvent(SRC, 40000, decoy, action, payload, Instant.now()));
    }

    /** Recon, a brute force, then a break-in with the honeytoken: DEMO steps 1-3. */
    private void intrusion() {
        feed("http-login", Action.CONNECT, "");
        feed("ftp", Action.CONNECT, "");
        feed("telnet", Action.CONNECT, "");
        for (int i = 1; i <= 5; i++) {
            feed("http-login", Action.LOGIN_ATTEMPT, "admin:guess" + i);
        }
        breakIn();
    }

    private void breakIn() {
        feed("http-login", Action.LOGIN_ATTEMPT, TOKEN);
        feed("http-login", Action.DECOY_ACCESS, TOKEN);
    }

    /** Wait until the engine has drained the queue and finished the last event. */
    private void settle() throws InterruptedException {
        long end = System.currentTimeMillis() + 5_000;
        while (!queue.isEmpty()) {
            assertTrue(System.currentTimeMillis() < end, "engine did not drain the queue");
            Thread.sleep(10);
        }
        Thread.sleep(300);
    }

    private long blocks() {
        return sent.stream().filter(c -> c.type() == CommandType.BLOCK).count();
    }

    @Test
    void theNextPacketAfterAnUnblockDoesNotReblockOrReRecordOldFindings() throws Exception {
        intrusion();
        settle();
        int findings = ledger.size();
        int commands = sent.size();
        assertTrue(blocks() >= 1, "the break-in is blocked: " + sent);

        engine.resetAlerts(SRC); // the operator pressed Unblock
        feed("http-login", Action.CONNECT, "");
        settle();

        assertEquals(findings, ledger.size(), "earlier findings are recorded once, not again");
        assertEquals(commands, sent.size(), "a plain connect after the unblock is not re-blocked: " + sent);
    }

    @Test
    void aNewBreakInAfterTheUnblockIsStillBlocked() throws Exception {
        intrusion();
        settle();
        long blocksBefore = blocks();
        int findings = ledger.size();

        engine.resetAlerts(SRC);
        feed("http-login", Action.CONNECT, "");
        breakIn();
        settle();

        assertTrue(blocks() > blocksBefore, "a new break-in is answered again: " + sent);
        assertTrue(ledger.size() > findings, "and recorded as a new finding");
    }

    @Test
    void theDossierKeepsTheWholeHistoryAcrossTheUnblock() throws Exception {
        intrusion();
        settle();
        engine.resetAlerts(SRC);
        feed("http-login", Action.CONNECT, "");
        settle();
        assertEquals(11, directory.snapshot(SRC).totalEvents(), "10 events before the unblock + 1 after");
    }
}
