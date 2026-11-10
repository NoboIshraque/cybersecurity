import com.mirage.console.ConsoleMain;
import com.mirage.console.ThreatLevel;
import com.mirage.console.ui.ThreatPopup;
import com.mirage.sensor.SensorMain;
import com.mirage.specter.SpecterMain;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Plays the Mirage demo by itself, paced for a screen recording of under two minutes.
 *
 * <p>Same idea as {@code tools/screenshots/LiveDemoShot.java} (the real Console, Sensor and
 * Specter in one JVM, all on 127.0.0.1, driven through their own buttons), but it lays the
 * windows out in a fixed grid on a dark backdrop, glides the real mouse pointer to each control
 * before pressing it, and waits for a {@code go.flag} file in the working folder before it
 * starts, so the recorder can be started first. It writes {@code done.flag} when the demo is
 * over and exits when {@code quit.flag} appears (or after ten minutes).
 *
 * <p>The layout is for a 1536x864 (logical) screen: the recording region is
 * {@code REGION} below, kept away from the screen edges.
 */
public class DemoVideo {

    private static final String[] FLAGS = {"--counterwatch-port=18080"};

    /** What gets recorded (logical pixels); everything below sits inside it. */
    static final Rectangle REGION = new Rectangle(8, 8, 1520, 800);

    private static final int SPLIT_X = 8 + 640;
    private static final Rectangle SPECTER = new Rectangle(8, 8, 640, 300);
    private static final Rectangle ATTACK_SLOT = new Rectangle(8, 308, 640, 500);
    private static final Rectangle DASH = new Rectangle(SPLIT_X, 8, 880, 800);
    private static final Rectangle POPUP = new Rectangle(SPLIT_X, 8, 880, 470);
    private static final Rectangle OP_SLOT = new Rectangle(SPLIT_X, 478, 880, 330);

    private static Robot robot;
    private static long t0;
    private static int shotNo;
    private static volatile boolean onTop;
    private static JFrame backdrop;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        new File("go.flag").delete();
        new File("done.flag").delete();
        new File("quit.flag").delete();

        SwingUtilities.invokeAndWait(DemoVideo::showBackdrop);
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e.getID() == WindowEvent.WINDOW_OPENED && e.getSource() instanceof Window w) {
                place(w);
            }
        }, AWTEvent.WINDOW_EVENT_MASK);

        background("console", () -> ConsoleMain.main(FLAGS));
        waitUntil(() -> frame("Mirage Console") != null, 20_000);
        background("sensor", () -> SensorMain.main(FLAGS));
        Thread.sleep(3000);
        background("specter", () -> SpecterMain.main(FLAGS));
        waitUntil(() -> frame("Specter — attack simulator") != null
                && frame("Specter — counter-watch") != null, 15_000);
        Thread.sleep(1500);
        onEdt(() -> {
            for (Frame f : Frame.getFrames()) {
                if (f.isDisplayable()) {
                    place(f);
                }
            }
            frame("Mirage Console").toFront();
            frame("Specter — counter-watch").toFront();
            frame("Specter — attack simulator").toFront();
            return true;
        });
        Thread.sleep(1000);
        robot.mouseMove(SPLIT_X - 40, 200);
        Files.writeString(new File("ready.flag").toPath(), "ready");
        System.out.println("READY - waiting for go.flag");
        while (!new File("go.flag").exists()) {
            if (new File("quit.flag").exists()) {
                System.exit(0);
            }
            Thread.sleep(200);
        }
        onTop = true;
        SwingUtilities.invokeAndWait(() -> {
            backdrop.setAlwaysOnTop(true);
            backdrop.toFront();
            for (String t : new String[] {"Mirage Console", "Specter — counter-watch", "Specter — attack simulator"}) {
                JFrame f = frame(t);
                if (f != null) {
                    f.setExtendedState(Frame.NORMAL);
                    f.setAlwaysOnTop(true);
                    f.toFront();
                }
            }
        });
        Thread.sleep(500);
        t0 = System.currentTimeMillis();

        try {
            play();
        } catch (Exception e) {
            e.printStackTrace();
            mark("ERROR " + e);
        }
        Files.writeString(new File("done.flag").toPath(), "done");
        mark("DONE");
        Thread.sleep(2000);
        if (!new File("stay.flag").exists()) {
            System.exit(0);
        }
        long deadline = System.currentTimeMillis() + 600_000;
        while (!new File("quit.flag").exists() && System.currentTimeMillis() < deadline) {
            Thread.sleep(300);
        }
        System.exit(0);
    }

    private static void play() throws Exception {
        mark("0 opening: dashboard (defender) right, Specter (attacker) left");
        Thread.sleep(6000);

        mark("1 recon scan");
        JFrame recon = launch("Recon scan", null);
        waitText(recon, "SCAN COMPLETE", 20_000);
        waitLevel(ThreatLevel.PROBE_DETECTED, 10_000);
        mark("  PROBE DETECTED");
        Thread.sleep(5000);

        mark("2 brute force");
        JFrame brute = launch("Brute force", null);
        waitText(brute, "NO ACCESS", 20_000);
        waitLevel(ThreatLevel.UNDER_ATTACK, 8_000);
        mark("  UNDER ATTACK");
        Thread.sleep(4500);

        mark("3 honeytoken replay (the trap)");
        JFrame replay = launch("Honeytoken replay", null);
        waitText(replay, "ACCESS GRANTED", 15_000);
        waitLevel(ThreatLevel.COMPROMISED, 10_000);
        mark("  COMPROMISED");
        Thread.sleep(6000);

        mark("4 operator: Tarpit");
        press(popup(), "Tarpit");
        JFrame defence = waitFrame("Mirage op shell — defence", 10_000);
        waitText(defence, "confirms: TARPIT", 15_000);
        Thread.sleep(1500);
        JFrame stalled = launch("Honeytoken replay", null);
        waitText(stalled, "CONNECTION STALLED", 20_000);
        mark("  CONNECTION STALLED");
        Thread.sleep(4000);

        mark("5 operator: Feed fake data");
        front(popup());
        press(popup(), "Feed fake data");
        waitText(defence, "confirms: FEED_FAKE", 15_000);
        Thread.sleep(1500);
        JFrame fooled = launch("Brute force", null);
        waitText(fooled, "ACCESS GRANTED", 15_000);
        mark("  fake files served");
        Thread.sleep(6000);

        mark("6 operator: Block");
        front(popup());
        press(popup(), "Block");
        waitText(defence, "confirms: BLOCK", 15_000);
        waitLevel(ThreatLevel.NEUTRALISED, 10_000);
        mark("  NEUTRALISED");
        Thread.sleep(3000);
        JFrame blocked = launch("Honeytoken replay", null);
        waitText(blocked, "CONNECTION BLOCKED", 15_000);
        mark("  CONNECTION BLOCKED");
        Thread.sleep(4000);

        mark("7 counter-attack (lab range only)");
        front(frame("Specter — counter-watch"));
        front(popup());
        press(popup(), "Counter-attack");
        JFrame counter = waitFrame("Mirage op shell — counter", 10_000);
        waitText(frame("Specter — counter-watch"), "INCOMING COUNTER-ATTACK", 15_000);
        mark("  INCOMING COUNTER-ATTACK");
        Thread.sleep(4000);
        front(popup());
        press(popup(), "Simultaneous counter");
        waitPanes(counter, 4, 20_000);
        waitText(counter, "COUNTERS LAUNCHED", 15_000);
        mark("  counters split across threads");
        Thread.sleep(6000);

        mark("8 verify ledger");
        JFrame dash = frame("Mirage Console");
        front(dash);
        Thread.sleep(800);
        javax.swing.JTable table = onEdtGet(() -> find(dash, javax.swing.JTable.class));
        if (table != null && table.getRowCount() > 0) {
            AtomicReference<Point> cell = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                Rectangle r = table.getCellRect(0, 1, true);
                Point p = table.getLocationOnScreen();
                cell.set(new Point(p.x + r.x + r.width / 2, p.y + r.y + r.height / 2));
            });
            glideToPoint(cell.get());
            SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));
            mark("  attacker dossier");
            Thread.sleep(3500);
        }
        press(dash, "Verify ledger");
        Thread.sleep(1500);
        mark("  ledger verified");
        Thread.sleep(3000);

        mark("9 out-of-range target refused");
        JFrame refused = launch("Recon scan", "8.8.8.8");
        waitText(refused, "REFUSED — OUTSIDE LAB RANGE", 10_000);
        mark("  REFUSED");
        Thread.sleep(6000);
    }

    // ---------------------------------------------------------------- layout

    private static void showBackdrop() {
        JFrame back = new JFrame("Mirage demo backdrop");
        backdrop = back;
        back.setUndecorated(true);
        back.getContentPane().setBackground(new Color(0x0d, 0x11, 0x17));
        back.setFocusableWindowState(false);
        back.setBounds(Toolkit.getDefaultToolkit().getScreenSize().width > 0
                ? new Rectangle(new Point(0, 0), Toolkit.getDefaultToolkit().getScreenSize())
                : REGION);
        back.setVisible(true);
    }

    private static void place(Window w) {
        if (onTop && w != backdrop && !w.isAlwaysOnTop()) {
            w.setAlwaysOnTop(true);
        }
        Rectangle r = slotFor(w);
        if (r != null && !r.equals(w.getBounds())) {
            w.setBounds(r);
        }
    }

    private static Rectangle slotFor(Window w) {
        if (w instanceof ThreatPopup) {
            return POPUP;
        }
        if (!(w instanceof Frame f) || f.getTitle() == null) {
            return null;
        }
        String t = f.getTitle();
        if (t.startsWith("Specter — attack simulator")) {
            return SPECTER;
        }
        if (t.startsWith("Specter shell") || t.startsWith("Specter — counter-watch")) {
            return ATTACK_SLOT;
        }
        if (t.startsWith("Mirage Console")) {
            return DASH;
        }
        if (t.startsWith("Mirage op shell") || t.startsWith("Mirage — Console pipeline")) {
            return OP_SLOT;
        }
        return null;
    }

    private static void front(Window w) throws Exception {
        if (w == null) {
            return;
        }
        onEdt(() -> {
            place(w);
            w.toFront();
            return true;
        });
        Thread.sleep(250);
    }

    // ---------------------------------------------------------------- pointer + controls

    /** Glide the real pointer to the middle of {@code c}. */
    private static void glideTo(Component c) throws Exception {
        AtomicReference<Point> target = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Point p = c.getLocationOnScreen();
            target.set(new Point(p.x + c.getWidth() / 2, p.y + c.getHeight() / 2));
        });
        glideToPoint(target.get());
    }

    private static void glideToPoint(Point to) throws Exception {
        Point from = MouseInfo.getPointerInfo().getLocation();
        int steps = 28;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double ease = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
            robot.mouseMove((int) Math.round(from.x + (to.x - from.x) * ease),
                    (int) Math.round(from.y + (to.y - from.y) * ease));
            Thread.sleep(16);
        }
        Thread.sleep(180);
    }

    private static void press(Container c, String text) throws Exception {
        AbstractButton b = onEdtGet(() -> button(c, text));
        if (b == null) {
            throw new IllegalStateException("no button '" + text + "'");
        }
        glideTo(b);
        SwingUtilities.invokeAndWait(() -> b.doClick(140));
        Thread.sleep(300);
    }

    /** Pick a campaign in Specter (optionally retype the target) and press Launch. */
    private static JFrame launch(String campaign, String host) throws Exception {
        JFrame specter = frame("Specter — attack simulator");
        front(specter);
        Set<Frame> before = new LinkedHashSet<>(List.of(Frame.getFrames()));
        if (host != null) {
            JTextField field = onEdtGet(() -> find(specter, JTextField.class));
            glideTo(field);
            SwingUtilities.invokeAndWait(() -> {
                field.requestFocusInWindow();
                field.setText("");
            });
            for (char ch : host.toCharArray()) {
                SwingUtilities.invokeAndWait(() -> field.setText(field.getText() + ch));
                Thread.sleep(110);
            }
            Thread.sleep(300);
        }
        JComboBox<?> box = onEdtGet(() -> find(specter, JComboBox.class));
        if (!campaign.equals(box.getSelectedItem())) {
            glideTo(box);
            SwingUtilities.invokeAndWait(() -> box.setSelectedItem(campaign));
            Thread.sleep(500);
        }
        press(specter, "Launch");
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
        front(created[0]);
        return created[0];
    }

    // ---------------------------------------------------------------- lookups and waits

    private static JFrame frame(String titlePrefix) {
        JFrame found = null;
        for (Frame f : Frame.getFrames()) {
            if (f.isDisplayable() && f.getTitle().startsWith(titlePrefix) && f instanceof JFrame jf) {
                found = jf;
            }
        }
        return found;
    }

    private static JFrame waitFrame(String titlePrefix, long timeout) throws Exception {
        waitUntil(() -> frame(titlePrefix) != null && frame(titlePrefix).isVisible(), timeout);
        JFrame f = frame(titlePrefix);
        if (f == null) {
            throw new IllegalStateException("window never opened: " + titlePrefix);
        }
        front(f);
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

    private static void waitLevel(ThreatLevel level, long timeout) throws Exception {
        if (!waitUntil(() -> popup() != null && popup().level() == level, timeout)) {
            mark("  (warning: popup did not reach " + level + ")");
        }
    }

    private static void waitText(Container c, String text, long timeout) throws Exception {
        if (!waitUntil(() -> allText(c).contains(text), timeout)) {
            mark("  (warning: never saw '" + text + "')");
        }
    }

    private static void waitPanes(Container c, int min, long timeout) throws Exception {
        waitUntil(() -> count(c, JTextPane.class) >= min, timeout);
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

    private interface Getter<T> {
        T get();
    }

    private static <T> T onEdtGet(Getter<T> g) throws Exception {
        AtomicReference<T> r = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> r.set(g.get()));
        return r.get();
    }

    private static AbstractButton button(Container c, String text) {
        for (Component child : c.getComponents()) {
            if (child instanceof AbstractButton b && text.equals(b.getText()) && b.isShowing()) {
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
        if (c == null) {
            return "";
        }
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

    // ---------------------------------------------------------------- output

    /** A timeline line (seconds since go.flag), for the voice-over. */
    private static void mark(String what) {
        String line = String.format("%6.1fs  %s", (System.currentTimeMillis() - t0) / 1000.0, what);
        System.out.println("@@ " + line);
        if (new File("shots").isDirectory() && robot != null) {
            try {
                java.awt.image.BufferedImage img = robot.createScreenCapture(REGION);
                java.awt.image.BufferedImage small = new java.awt.image.BufferedImage(
                        img.getWidth() * 3 / 4, img.getHeight() * 3 / 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = small.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(img, 0, 0, small.getWidth(), small.getHeight(), null);
                g.dispose();
                javax.imageio.ImageIO.write(small, "png", new File("shots", String.format("%02d.png", shotNo++)));
            } catch (Exception ignored) {
                // snapshots are for checking only
            }
        }
        try {
            Files.writeString(new File("timeline.txt").toPath(), line + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // the timeline is a convenience
        }
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
