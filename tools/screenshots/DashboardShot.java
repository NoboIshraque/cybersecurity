import com.mirage.common.Action;
import com.mirage.common.AttackEvent;
import com.mirage.common.AttackerProfile;
import com.mirage.common.Alert;
import com.mirage.common.Classification;
import com.mirage.common.LedgerEntry;
import com.mirage.common.ScopeGuard;
import com.mirage.common.Severity;
import com.mirage.console.AlertBus;
import com.mirage.console.AlertLedger;
import com.mirage.console.AttackerDirectory;
import com.mirage.console.CommandSink;
import com.mirage.console.CounterOffensive;
import com.mirage.console.KeyPairSigner;
import com.mirage.console.MirrorCounterStrategy;
import com.mirage.console.ResponseController;
import com.mirage.console.ui.Dashboard;
import com.mirage.specter.Attacker;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

/**
 * Renders the real Dashboard populated with sample activity and grabs a figure for
 * REPORT.md Section 5. Arg: dossier (default) | replay | responses | broken.
 */
public class DashboardShot {

    private static final Instant T0 = Instant.parse("2026-10-14T00:12:00Z");

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "dossier";

        ScopeGuard guard = new ScopeGuard(List.of("10.10.0.0/24", "127.0.0.1/32"));
        AlertLedger ledger = new AlertLedger(new KeyPairSigner());
        AlertBus bus = new AlertBus();
        AttackerDirectory directory = new AttackerDirectory();
        ResponseController responses = new ResponseController(guard, CommandSink.NONE);
        CounterOffensive counter = new CounterOffensive(
                new Attacker(guard, line -> { }), new MirrorCounterStrategy(), 8080);

        seedActivity(directory);

        Dashboard dash = new Dashboard(ledger, () -> 2, directory, responses, counter);
        bus.subscribe(dash::onAlert);
        SwingUtilities.invokeAndWait(() -> dash.setVisible(true));

        // publish oldest -> newest so the most severe end up on top
        String[][] rows = {
            {"10.10.0.5", "Port / service scan", "T1046", "MEDIUM"},
            {"10.10.0.5", "Password spraying", "T1110.003", "HIGH"},
            {"10.10.0.5", "Credential brute-force", "T1110", "HIGH"},
            {"10.10.0.9", "Multi-host activity (lateral movement)", "T1021", "HIGH"},
            {"10.10.0.7", "Bait file tampered (possible ransomware)", "T1485", "CRITICAL"},
            {"10.10.0.7", "Stolen credentials used (honeytoken)", "T1078", "CRITICAL"},
        };
        for (String[] r : rows) {
            Alert alert = new Alert(r[0], new Classification(r[1], r[2], Severity.valueOf(r[3])), T0);
            ledger.append(alert);
            bus.publish(alert);
            Thread.sleep(80);
        }
        Thread.sleep(500);

        // select the brute-force row for 10.10.0.5 (row 3, newest-first) to populate the detail view
        JTable table = field(dash, "table", JTable.class);
        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(3, 3));
        Thread.sleep(300);

        JTabbedPane tabs = findTabs(field(dash, "detail", Container.class));
        switch (mode) {
            case "replay" -> SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(1));
            case "responses" -> SwingUtilities.invokeAndWait(() -> {
                tabs.setSelectedIndex(2);
                AbstractButton tarpit = findButton(dash, "Tarpit");
                if (tarpit != null) tarpit.doClick();
                AbstractButton block = findButton(dash, "Block");
                if (block != null) block.doClick();
                AbstractButton ca = findButton(dash, "Counter-attack");
                if (ca != null) ca.doClick();
            });
            case "broken" -> {
                tamperHash(ledger, 2); // corrupt the 3rd entry's hash, keep its alert
                AbstractButton verify = findButton(dash, "Verify ledger");
                SwingUtilities.invokeAndWait(() -> { if (verify != null) verify.doClick(); });
            }
            default -> SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(0));
        }
        Thread.sleep(600);
        Thread.sleep(120000);
    }

    private static void seedActivity(AttackerDirectory dir) {
        // 10.10.0.5 : scan -> spray -> brute-force across HTTP and FTP decoys
        dir.update(profile("10.10.0.5",
                ev("10.10.0.5", "scan", Action.PORT_PROBE, "tcp/8080", 0),
                ev("10.10.0.5", "scan", Action.PORT_PROBE, "tcp/8081", 1),
                ev("10.10.0.5", "scan", Action.PORT_PROBE, "tcp/8022", 2),
                ev("10.10.0.5", "http-login", Action.LOGIN_ATTEMPT, "admin:Password123!", 6),
                ev("10.10.0.5", "http-login", Action.LOGIN_ATTEMPT, "root:Password123!", 7),
                ev("10.10.0.5", "http-login", Action.LOGIN_ATTEMPT, "admin:123456", 11),
                ev("10.10.0.5", "http-login", Action.LOGIN_ATTEMPT, "admin:password", 12),
                ev("10.10.0.5", "ftp", Action.LOGIN_ATTEMPT, "admin:letmein", 13)));
        // 10.10.0.9 : same stolen credential tried on two hosts (lateral movement)
        dir.update(profile("10.10.0.9",
                tagged("10.10.0.9", "http-login", Action.LOGIN_ATTEMPT, "svc_app:S3rvice!", 20, "host-a"),
                tagged("10.10.0.9", "ftp", Action.LOGIN_ATTEMPT, "svc_app:S3rvice!", 24, "host-b")));
        // 10.10.0.7 : honeytoken + bait file (critical)
        dir.update(profile("10.10.0.7",
                ev("10.10.0.7", "http-login", Action.LOGIN_ATTEMPT, "admin:admin", 30),
                ev("10.10.0.7", "http-login", Action.HONEYTOKEN_USE, "svc_backup:S3cr3tBackup!", 33),
                ev("10.10.0.7", "bait-file", Action.FILE_CHANGE, "config/backup.cfg", 35)));
        // 10.10.0.12 : one probe, no alert yet -> stays grey on the map
        dir.update(profile("10.10.0.12",
                ev("10.10.0.12", "scan", Action.PORT_PROBE, "tcp/8080", 40)));
    }

    private static AttackerProfile profile(String ip, AttackEvent... events) {
        AttackerProfile p = new AttackerProfile(ip);
        for (AttackEvent e : events) p.add(e);
        return p;
    }

    private static AttackEvent ev(String ip, String decoy, Action a, String payload, int sec) {
        return new AttackEvent(ip, 50000 + sec, decoy, a, payload, T0.plusSeconds(sec));
    }

    private static AttackEvent tagged(String ip, String decoy, Action a, String payload, int sec, String sensor) {
        return ev(ip, decoy, a, payload, sec).withSensor(sensor);
    }

    @SuppressWarnings("unchecked")
    private static void tamperHash(AlertLedger ledger, int index) throws Exception {
        Field f = AlertLedger.class.getDeclaredField("chain");
        f.setAccessible(true);
        List<LedgerEntry> chain = (List<LedgerEntry>) f.get(ledger);
        LedgerEntry e = chain.get(index);
        chain.set(index, new LedgerEntry(e.prevHash(), e.hash() + "ff", e.alert(), e.signature()));
    }

    private static <T> T field(Object obj, String name, Class<T> type) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return type.cast(f.get(obj));
    }

    private static JTabbedPane findTabs(Container c) {
        for (Component child : c.getComponents()) {
            if (child instanceof JTabbedPane t) return t;
            if (child instanceof Container inner) {
                JTabbedPane t = findTabs(inner);
                if (t != null) return t;
            }
        }
        return null;
    }

    private static AbstractButton findButton(Container c, String text) {
        for (Component child : c.getComponents()) {
            if (child instanceof JButton b && text.equals(b.getText())) return b;
            if (child instanceof Container inner) {
                AbstractButton b = findButton(inner, text);
                if (b != null) return b;
            }
        }
        return null;
    }
}
