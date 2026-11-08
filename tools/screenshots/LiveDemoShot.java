import com.mirage.console.ConsoleMain;
import com.mirage.console.ThreatLevel;
import com.mirage.console.ui.ThreatPopup;
import com.mirage.sensor.SensorMain;
import com.mirage.specter.SpecterMain;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Phase 8 dry run of the live-shells demo on one machine, and the source of the
 * {@code docs/screenshots/live-*.png} figures.
 *
 * <p>Runs the real Console, Sensor and Specter in one JVM (default config: lab
 * range 10.10.0.0/24 + 127.0.0.1/32, everything on 127.0.0.1), then drives them
 * through their own buttons exactly as the presenters would: every Specter
 * campaign (one at a time and all at once), every operator action from the threat
 * popup and the dashboard, auto-response off and on, a counter landing on the
 * counter-watch, and an out-of-range target being refused. Each step checks what
 * appears on screen and prints PASS/FAIL; key windows are rendered to PNG.
 *
 * <p>Run from an empty scratch directory (it creates {@code data/} and {@code bait/}):
 * <pre>
 *   javac -cp target/classes:&lt;deps&gt; -d out tools/screenshots/LiveDemoShot.java
 *   java  -cp out:target/classes:&lt;deps&gt; LiveDemoShot &lt;screenshot-dir&gt;
 * </pre>
 * Exit code 0 only if every check passed.
 */
public class LiveDemoShot {

    /** Its own counter-watch port, so a busy :8080 on the build machine cannot skew the run. */
    private static final String[] FLAGS = {"--counterwatch-port=18080"};

    private static final List<String> results = new ArrayList<>();
    private static boolean failed;
    private static File outDir;

    public static void main(String[] args) throws Exception {
        outDir = new File(args.length > 0 ? args[0] : "shots");
        outDir.mkdirs();

        background("console", () -> ConsoleMain.main(FLAGS));
        waitUntil(() -> frame("Mirage Console") != null, 20_000);
        background("sensor", () -> SensorMain.main(FLAGS));
        Thread.sleep(3000);
        background("specter", () -> SpecterMain.main(FLAGS));
        waitUntil(() -> frame("Specter — attack simulator") != null
                && frame("Specter — counter-watch") != null, 15_000);
        check("defender + attacker roles up (dashboard, Specter, counter-watch)",
                frame("Mirage Console") != null && frame("Specter — attack simulator") != null
                        && frame("Specter — counter-watch") != null);
        check("auto-response starts OFF", !autoBox().isSelected());

        // ---- 1. every campaign, one at a time -----------------------------------------
        JFrame recon = launch("Recon scan", false, "127.0.0.1");
        check("recon: SCAN COMPLETE", waitText(recon, "SCAN COMPLETE", 15_000));
        check("console popup: PROBE DETECTED", waitLevel(ThreatLevel.PROBE_DETECTED, 10_000));
        shot(recon, "live-specter-recon");

        JFrame spray = launch("Password spray", false, "127.0.0.1");
        check("spray: NO ACCESS", waitText(spray, "NO ACCESS", 20_000));
        check("console popup: UNDER ATTACK", waitLevel(ThreatLevel.UNDER_ATTACK, 10_000));

        JFrame brute = launch("Brute force", false, "127.0.0.1");
        check("brute force: NO ACCESS", waitText(brute, "NO ACCESS", 20_000));

        JFrame replay = launch("Honeytoken replay", false, "127.0.0.1");
        check("honeytoken replay: ACCESS GRANTED (decoy fake admin page)",
                waitText(replay, "ACCESS GRANTED", 15_000));
        check("console popup: COMPROMISED", waitLevel(ThreatLevel.COMPROMISED, 10_000));
        check("popup says it is waiting for the operator",
                waitText(popup(), "COMPROMISED", 5000) && labelText(popup()).contains("waiting for the operator"));
        int afterCampaigns = alertCount();
        check("alerts recorded once per finding, not per event (" + afterCampaigns + " alerts)",
                afterCampaigns > 0 && afterCampaigns <= 10);
        shot(replay, "live-specter-access-granted");
        Thread.sleep(1500);
        shot(popup(), "live-threat-popup-compromised");

        // ---- 2. a concurrent campaign (split threads) -----------------------------------
        JFrame full = launch("Full campaign", true, "127.0.0.1");
        check("full campaign all at once: one pane per thread", waitPanes(full, 4, 20_000));
        waitText(full, "SCAN COMPLETE", 20_000);
        Thread.sleep(1500);
        shot(full, "live-specter-concurrent");

        // ---- 3. operator responses from the threat popup --------------------------------
        click(popup(), "Tarpit");
        JFrame defence = waitFrame("Mirage op shell — defence", 10_000);
        check("tarpit: op shell shows ScopeGuard + sensor ACK",
                waitText(defence, "confirms: TARPIT", 15_000) && waitText(defence, "ScopeGuard.require", 1000));
        JFrame stalled = launch("Honeytoken replay", false, "127.0.0.1");
        check("specter after tarpit: CONNECTION STALLED", waitText(stalled, "CONNECTION STALLED", 15_000));
        shot(stalled, "live-specter-stalled");

        click(popup(), "Feed fake data");
        check("feed fake data: sensor ACK", waitText(defence, "confirms: FEED_FAKE", 15_000));
        // B-09: fake data replaces the tarpit, so a wrong password is now let "in" to the lures
        JFrame fooled = launch("Brute force", false, "127.0.0.1");
        check("specter after fake data: ACCESS GRANTED (fake files)", waitText(fooled, "ACCESS GRANTED", 15_000));

        click(popup(), "Block");
        check("block: sensor ACK in op shell", waitText(defence, "confirms: BLOCK", 15_000));
        check("console popup: NEUTRALISED", waitLevel(ThreatLevel.NEUTRALISED, 10_000));
        Thread.sleep(1500);
        shot(defence, "live-op-shell-block");
        shot(popup(), "live-threat-popup-neutralised");
        JFrame blocked = launch("Honeytoken replay", false, "127.0.0.1");
        check("specter after block: CONNECTION BLOCKED", waitText(blocked, "CONNECTION BLOCKED", 15_000));
        shot(blocked, "live-specter-blocked");

        // ---- 4. counters, landing on the attacker's counter-watch --------------------------
        click(popup(), "Counter-attack");
        JFrame counterShell = waitFrame("Mirage op shell — counter", 10_000);
        JFrame watch = frame("Specter — counter-watch");
        check("counter: in range, launched", waitText(counterShell, "in range", 15_000));
        check("counter-watch: INCOMING COUNTER-ATTACK", waitText(watch, "INCOMING COUNTER-ATTACK", 15_000));
        click(popup(), "Simultaneous counter");
        check("simultaneous counter: split, one pane per counter thread", waitPanes(counterShell, 4, 20_000));
        waitText(counterShell, "COUNTERS LAUNCHED", 15_000);
        check("counters against the silent counter-watch read NO REPLY, not BLOCKED",
                waitText(counterShell, "NO REPLY", 15_000) && !allText(counterShell).contains("CONNECTION BLOCKED"));
        Thread.sleep(2500);
        shot(counterShell, "live-op-shell-counter-split");
        shot(watch, "live-counterwatch-incoming");

        // ---- 5. unblock from the dashboard, then auto-response ON ----------------------
        click(popup(), "Inspect in dashboard");
        Thread.sleep(500);
        click(frame("Mirage Console"), "Unblock");
        check("unblock: popup back to COMPROMISED", waitLevel(ThreatLevel.COMPROMISED, 15_000));

        click(frame("Mirage Console"), "Auto-response");
        check("auto-response checkbox ON", autoBox().isSelected());
        JFrame auto = launch("Honeytoken replay", false, "127.0.0.1");
        waitText(auto, "ACCESS GRANTED", 15_000);
        check("auto-response: Console blocks by itself -> NEUTRALISED",
                waitLevel(ThreatLevel.NEUTRALISED, 15_000));
        click(frame("Mirage Console"), "Pipeline shell");
        JFrame pipeline = waitFrame("Mirage — Console pipeline", 10_000);
        check("pipeline shell shows auto-response dispatch",
                waitText(pipeline, "auto-response: ", 20_000));
        Thread.sleep(2500);
        shot(pipeline, "live-pipeline-shell");
        shot(frame("Mirage Console"), "live-dashboard");

        // ---- 6. safety: a target outside the lab range ------------------------------
        JFrame refused = launch("Recon scan", false, "8.8.8.8");
        check("out-of-range target: REFUSED - OUTSIDE LAB RANGE",
                waitText(refused, "REFUSED — OUTSIDE LAB RANGE", 10_000));
        shot(refused, "live-specter-refused");

        System.out.println();
        results.forEach(System.out::println);
        System.out.println(failed ? "DRY RUN: FAILURES" : "DRY RUN: ALL PASS");
        System.exit(failed ? 1 : 0);
    }

    // ---------------------------------------------------------------- Specter driving

    /** Pick a campaign in the real Specter window and press Launch; returns its new shell window. */
    private static JFrame launch(String campaign, boolean allAtOnce, String host) throws Exception {
        JFrame specter = frame("Specter — attack simulator");
        Set<Frame> before = new LinkedHashSet<>(List.of(Frame.getFrames()));
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<?> box = find(specter, JComboBox.class);
            box.setSelectedItem(campaign);
            AbstractButton concurrent = button(specter, "All at once (split threads)");
            if (concurrent.isSelected() != allAtOnce) {
                concurrent.doClick();
            }
            find(specter, JTextField.class).setText(host);
            button(specter, "Launch").doClick();
        });
        JFrame[] created = new JFrame[1];
        waitUntil(() -> {
            for (Frame f : Frame.getFrames()) {
                if (!before.contains(f) && f.getTitle().startsWith("Specter shell") && f instanceof JFrame jf) {
                    created[0] = jf;
                    return true;
                }
            }
            return false;
        }, 10_000);
        if (created[0] == null) {
            throw new IllegalStateException("no Specter shell opened for " + campaign);
        }
        return created[0];
    }

    // ---------------------------------------------------------------- lookups and waits

    private static JFrame frame(String titlePrefix) {
        JFrame found = null;
        for (Frame f : Frame.getFrames()) {
            if (f.isDisplayable() && f.getTitle().startsWith(titlePrefix) && f instanceof JFrame jf) {
                found = jf; // newest wins
            }
        }
        return found;
    }

    private static JFrame waitFrame(String titlePrefix, long timeout) throws InterruptedException {
        waitUntil(() -> frame(titlePrefix) != null && frame(titlePrefix).isVisible(), timeout);
        JFrame f = frame(titlePrefix);
        if (f == null) {
            throw new IllegalStateException("window never opened: " + titlePrefix);
        }
        return f;
    }

    private static ThreatPopup popup() {
        for (Frame f : Frame.getFrames()) {
            if (f instanceof ThreatPopup p && "127.0.0.1".equals(p.sourceIp())) {
                return p;
            }
        }
        return null;
    }

    private static boolean waitLevel(ThreatLevel level, long timeout) throws InterruptedException {
        return waitUntil(() -> popup() != null && popup().level() == level, timeout);
    }

    private static boolean waitText(Container c, String text, long timeout) throws InterruptedException {
        return waitUntil(() -> allText(c).contains(text), timeout);
    }

    private static boolean waitPanes(Container c, int min, long timeout) throws InterruptedException {
        // in split view the main stream is detached, so every JTextPane left is a thread pane
        return waitUntil(() -> count(c, JTextPane.class) >= min, timeout);
    }

    private static boolean waitUntil(BooleanSupplier condition, long timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            if (onEdt(condition)) {
                return true;
            }
            Thread.sleep(150);
        }
        return onEdt(condition);
    }

    private static boolean onEdt(BooleanSupplier condition) {
        AtomicReference<Boolean> r = new AtomicReference<>(false);
        try {
            SwingUtilities.invokeAndWait(() -> r.set(condition.getAsBoolean()));
        } catch (Exception e) {
            return false;
        }
        return r.get();
    }

    /** The "Alerts: N" figure from the dashboard status bar. */
    private static int alertCount() throws InterruptedException {
        Thread.sleep(2500); // the status bar refreshes on alerts and every 2 s
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Alerts: (\\d+)")
                .matcher(allText(frame("Mirage Console")));
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static AbstractButton autoBox() {
        return button(frame("Mirage Console"), "Auto-response");
    }

    private static void click(Container c, String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AbstractButton b = button(c, text);
            if (b == null) {
                throw new IllegalStateException("no button '" + text + "'");
            }
            b.doClick();
        });
    }

    private static AbstractButton button(Container c, String text) {
        for (Component child : c.getComponents()) {
            if (child instanceof AbstractButton b && text.equals(b.getText())) {
                return b;
            }
            if (child instanceof Container inner) {
                AbstractButton found = button(inner, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T> T find(Container c, Class<T> type) {
        for (Component child : c.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container inner) {
                T found = find(inner, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int count(Container c, Class<?> type) {
        int n = 0;
        for (Component child : c.getComponents()) {
            if (type.isInstance(child)) {
                n++;
            }
            if (child instanceof Container inner) {
                n += count(inner, type);
            }
        }
        return n;
    }

    private static String allText(Container c) {
        StringBuilder sb = new StringBuilder();
        collectText(c, sb);
        return sb.toString();
    }

    private static void collectText(Container c, StringBuilder sb) {
        for (Component child : c.getComponents()) {
            if (child instanceof JTextComponent t) {
                sb.append(t.getText()).append('\n');
            } else if (child instanceof javax.swing.JLabel l) {
                sb.append(l.getText()).append('\n');
            }
            if (child instanceof Container inner) {
                collectText(inner, sb);
            }
        }
    }

    private static String labelText(Container c) {
        StringBuilder sb = new StringBuilder();
        collectText(c, sb);
        return sb.toString();
    }

    // ---------------------------------------------------------------- output

    private static void check(String what, boolean ok) {
        String line = (ok ? "PASS  " : "FAIL  ") + what;
        results.add(line);
        System.out.println(">>> " + line);
        failed |= !ok;
    }

    /** Render the window's contents (no OS title bar) to docs-style PNG. */
    private static void shot(JFrame frame, String name) throws Exception {
        if (frame == null) {
            check("screenshot " + name + " (window missing)", false);
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            Component root = frame.getRootPane();
            BufferedImage img = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            root.printAll(g);
            g.dispose();
            try {
                ImageIO.write(img, "png", new File(outDir, name + ".png"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        System.out.println("    saved " + name + ".png");
    }

    private interface Body {
        void run() throws Exception;
    }

    private static void background(String name, Body body) {
        Thread t = new Thread(() -> {
            try {
                body.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, name + "-main");
        t.setDaemon(true);
        t.start();
    }
}
