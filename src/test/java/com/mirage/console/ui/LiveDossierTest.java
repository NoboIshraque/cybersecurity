package com.mirage.console.ui;

import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.console.AttackerDirectory;
import com.mirage.console.AttackerDossier;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-18: the dossier follows new activity of the selected source, counts a scan's connections
 * as port probes, lists a trap credential that got in as a honeytoken used, and shows only the
 * last credentials with a total instead of a wall of text.
 */
class LiveDossierTest {

    private static final String SRC = "10.10.0.5";

    private static AttackEvent event(String decoy, Action action, String payload) {
        return new AttackEvent(SRC, 4444, decoy, action, payload, Instant.now());
    }

    private static AttackerDossier dossierOf(AttackerProfile p) {
        AttackerDirectory directory = new AttackerDirectory();
        directory.update(p);
        return AttackerDossier.from(directory.snapshot(SRC));
    }

    @Test
    void aScansConnectionsArePortProbes() {
        AttackerProfile p = new AttackerProfile(SRC);
        for (String decoy : new String[]{"http-login", "ftp", "telnet"}) {
            p.add(event(decoy, Action.CONNECT, ""));
        }
        assertEquals(3, dossierOf(p).portProbes());
    }

    @Test
    void aTrapCredentialThatGotInIsAHoneytokenUsed() {
        AttackerProfile p = new AttackerProfile(SRC);
        p.add(event("http-login", Action.LOGIN_ATTEMPT, "svc_backup:S3cr3tBackup!"));
        p.add(event("http-login", Action.DECOY_ACCESS, "svc_backup:S3cr3tBackup!"));
        assertEquals(java.util.List.of("svc_backup:S3cr3tBackup!"), dossierOf(p).honeytokensUsed());
    }

    @Test
    void longBruteForceShowsTheTotalAndOnlyTheLastCredentials() {
        AttackerProfile p = new AttackerProfile(SRC);
        for (int i = 0; i < 40; i++) {
            p.add(event("http-login", Action.LOGIN_ATTEMPT, "admin:guess" + i));
        }
        String text = AttackerDetailPanel.renderDossier(dossierOf(p));
        String line = text.lines().filter(l -> l.contains("Credentials tried")).findFirst().orElseThrow();
        assertTrue(line.contains("40"), line);
        assertTrue(line.contains("admin:guess39"), line);
        assertFalse(line.contains("admin:guess0,") || line.contains("admin:guess0 "), "oldest left out: " + line);
    }

    @Test
    void theSelectedSourceFollowsNewActivity() throws Exception {
        AttackerDirectory directory = new AttackerDirectory();
        AttackerProfile p = new AttackerProfile(SRC);
        p.add(event("http-login", Action.CONNECT, ""));
        directory.update(p);
        AtomicReference<AttackerDetailPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            panel.set(new AttackerDetailPanel(directory, (op, ip, done) -> { }));
            panel.get().showSource(SRC);
        });
        p.add(event("ftp", Action.CONNECT, ""));
        p.add(event("ftp", Action.LOGIN_ATTEMPT, "root:toor"));
        directory.update(p);
        AtomicReference<String> text = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            panel.get().refresh();
            text.set(panel.get().dossierText());
        });
        assertTrue(text.get().contains("root:toor"), text.get());
        assertTrue(text.get().lines().anyMatch(l -> l.contains("Events observed") && l.contains("3")), text.get());
    }
}
