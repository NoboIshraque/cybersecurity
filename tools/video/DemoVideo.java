import com.mirage.console.ConsoleMain;
import com.mirage.console.ThreatLevel;
import com.mirage.console.ui.ThreatPopup;
import com.mirage.sensor.SensorMain;
import com.mirage.specter.SpecterMain;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.JTextComponent;
import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.swing.RepaintManager;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Plays the Mirage demo by itself and records it, for the demo video (under two minutes).
 *
 * <p>The real Console, Sensor and Specter run in one JVM on 127.0.0.1 and are driven through
 * their own buttons, as in {@code tools/screenshots/LiveDemoShot.java}. Nothing is filmed from
 * the screen: every frame is composed here, 30 times a second, by painting each window's
 * contents ({@code printAll}) into a 1920x1080 image at its place in a fixed layout, and the
 * frames are piped to ffmpeg. On top of the windows the composer draws what a person would see:
 * a pointer that glides to each control on a slightly curved, eased path; a ripple where it
 * clicks; a lower-third title for each part; and a title card and a closing card. Because the
 * desktop is never recorded, nothing else on the screen (Claude's own glowing edges included)
 * can get into the video, and a bad take is just run again.
 *
 * <p>Usage: {@code java DemoVideo out.mp4}; ffmpeg is taken from the {@code FFMPEG} environment
 * variable, else from the PATH. It writes {@code timeline.txt} (video time of each step) and,
 * when a {@code shots} folder exists, a still at each step.
 */
public class DemoVideo {

    private static final String[] FLAGS = {"--counterwatch-port=18080"};

    /** The canvas in logical pixels; the video is this times {@link #SCALE}. */
    private static final int W = 1536;
    private static final int H = 864;
    private static final double SCALE = 1.25;
    private static final int OUT_W = 1920;
    private static final int OUT_H = 1080;
    private static final int FPS = 30;
    /** Height of the title bar drawn above each window's contents. */
    private static final int TITLE = 30;

    // Specter (attacker) on the left, the Mirage Console (defender) on the right.
    private static final Rectangle SPECTER = new Rectangle(16, 16, 640, 300);
    private static final Rectangle ATTACK_SLOT = new Rectangle(16, 328, 640, 520);
    private static final Rectangle DASH = new Rectangle(668, 16, 852, 832);
    private static final Rectangle POPUP = new Rectangle(668, 16, 852, 460);
    private static final Rectangle OP_SLOT = new Rectangle(668, 488, 852, 360);

    private static final Color ACCENT = new Color(0x2B, 0xB3, 0xC0);

    // ------------------------------------------------------------------ what the composer draws

    private record Ripple(double x, double y, long startNanos) { }

    private record Caption(String title, String sub, long startNanos, long endNanos) { }

    private record Card(boolean closing, long startNanos, long endNanos) { }

    private static volatile double curX = 560;
    private static volatile double curY = 180;
    private static final List<Ripple> RIPPLES = new CopyOnWriteArrayList<>();
    private static volatile Caption caption;
    private static volatile Card card;
    /** Paint order, bottom first. Only touched on the EDT. */
    private static final List<Window> Z = new ArrayList<>();
    private static final Map<Window, ThreatLevel> LAST_LEVEL = new HashMap<>();
    private static final Random RND = new Random(7);

    private static BufferedImage frame;
    private static BufferedImage backdropImage;
    /** Each window's contents at video resolution, repainted only where Swing says it changed. */
    private static final Map<Window, BufferedImage> CACHE = new HashMap<>();
    private static final Map<Window, Rectangle> DIRTY = new HashMap<>();
    private static final Map<Window, Long> LAST_FULL = new HashMap<>();
    private static final Map<String, Long> PRINT_NANOS = new java.util.TreeMap<>();
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font CAPTION_TITLE = new Font("Segoe UI Semibold", Font.PLAIN, 22);
    private static final Font CAPTION_SUB = new Font("Segoe UI", Font.PLAIN, 15);
    private static volatile boolean recording;
    private static long recStart;
    private static int shotNo;
    private static AbstractButton hovered;
    private static long composeNanos;
    private static int composed;
    private static int written;

    public static void main(String[] args) throws Exception {
        // grey-scale text antialiasing: subpixel (LCD) text gives coloured fringes in a video
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        File out = new File(args.length > 0 ? args[0] : "mirage-demo.mp4");
        String ffmpeg = System.getenv().getOrDefault("FFMPEG", "ffmpeg");
        SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(new Tracker()));

        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e.getSource() instanceof Window w) {
                if (e.getID() == WindowEvent.WINDOW_OPENED) {
                    place(w);
                    raiseOnEdt(w);
                } else if (e.getID() == WindowEvent.WINDOW_ACTIVATED) {
                    raiseOnEdt(w);
                }
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
        SwingUtilities.invokeAndWait(() -> {
            for (Frame f : Frame.getFrames()) {
                if (f.isDisplayable()) {
                    place(f);
                }
            }
            raise(frame("Mirage Console"));
            raise(frame("Specter — counter-watch"));
            raise(frame("Specter — attack simulator"));
        });
        Thread.sleep(1500);

        frame = new BufferedImage(OUT_W, OUT_H, BufferedImage.TYPE_INT_RGB);
        backdropImage = new BufferedImage(OUT_W, OUT_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D bg = backdropImage.createGraphics();
        bg.scale(SCALE, SCALE);
        bg.setPaint(new GradientPaint(0, 0, new Color(0x0B, 0x12, 0x20), 0, H, new Color(0x10, 0x1B, 0x2C)));
        bg.fillRect(0, 0, W, H);
        bg.dispose();
        Thread recorder = startRecorder(ffmpeg, out);
        long now = System.nanoTime();
        card = new Card(false, now - 1_000_000_000L, now + 4_600_000_000L);
        Thread.sleep(5200);

        try {
            play();
        } catch (Exception e) {
            e.printStackTrace();
            mark("ERROR " + e);
        }
        now = System.nanoTime();
        card = new Card(true, now, now + 60_000_000_000L);
        mark("closing card");
        Thread.sleep(5000);
        mark("END");
        recording = false;
        recorder.join(30_000);
        System.out.printf("composed %d frames, %.1f ms each (%.1f ms drawing); wrote %d frames%n",
                composed, composeNanos / 1e6 / Math.max(1, composed), bodyNanos / 1e6 / Math.max(1, composed), written);
        System.out.printf("writing to ffmpeg: %.1f ms per composed frame%n", writeNanos / 1e6 / Math.max(1, composed));
        System.out.printf("per frame: backdrop %.1f, windows %.1f, popups %.1f, overlays %.1f, cards %.1f ms%n",
                SECTION[0] / 1e6 / Math.max(1, composed), SECTION[1] / 1e6 / Math.max(1, composed),
                SECTION[2] / 1e6 / Math.max(1, composed), SECTION[3] / 1e6 / Math.max(1, composed),
                SECTION[4] / 1e6 / Math.max(1, composed));
        System.out.printf("windows: shadow+bar+contents %.1f, copy %.1f ms per frame%n",
                PW[0] / 1e6 / Math.max(1, composed), PW[2] / 1e6 / Math.max(1, composed));
        PRINT_NANOS.forEach((k, v) -> System.out.printf("  printing %-40s %8.0f ms in all%n", k, v / 1e6));
        System.exit(0);
    }

    private static void play() throws Exception {
        mark("1 recon scan");
        caption("Recon · port scan", "Specter touches the decoys: PROBE DETECTED");
        JFrame recon = launch("Recon scan", null);
        waitText(recon, "SCAN COMPLETE", 20_000);
        waitLevel(ThreatLevel.PROBE_DETECTED, 10_000);
        mark("  PROBE DETECTED");
        Thread.sleep(5000);

        mark("2 brute force");
        caption("Brute force", "Password guessing on the decoy login: UNDER ATTACK");
        JFrame brute = launch("Brute force", null);
        waitText(brute, "NO ACCESS", 20_000);
        waitLevel(ThreatLevel.UNDER_ATTACK, 8_000);
        mark("  UNDER ATTACK");
        Thread.sleep(5000);

        mark("3 honeytoken replay (the trap)");
        caption("Honeytoken replay", "A planted credential is used: COMPROMISED");
        JFrame replay = launch("Honeytoken replay", null);
        waitText(replay, "ACCESS GRANTED", 15_000);
        waitLevel(ThreatLevel.COMPROMISED, 10_000);
        mark("  COMPROMISED");
        Thread.sleep(6000);

        mark("4 operator: Tarpit");
        caption("Response · Tarpit", "The operator holds the attacker on the line");
        raise(popup());
        press(popup(), "Tarpit");
        JFrame defence = waitFrame("Mirage op shell — defence", 10_000);
        waitText(defence, "confirms: TARPIT", 15_000);
        Thread.sleep(1200);
        JFrame stalled = launch("Honeytoken replay", null);
        waitText(stalled, "CONNECTION STALLED", 20_000);
        mark("  CONNECTION STALLED");
        Thread.sleep(4500);

        mark("5 operator: Feed fake data");
        caption("Response · Feed fake data", "The attacker gets in, to fake files");
        raise(popup());
        press(popup(), "Feed fake data");
        waitText(defence, "confirms: FEED_FAKE", 15_000);
        Thread.sleep(1200);
        JFrame fooled = launch("Brute force", null);
        waitText(fooled, "ACCESS GRANTED", 15_000);
        mark("  fake files served");
        Thread.sleep(6000);

        mark("6 operator: Block");
        caption("Response · Block", "The sensor cuts the source off: NEUTRALISED");
        raise(popup());
        press(popup(), "Block");
        waitText(defence, "confirms: BLOCK", 15_000);
        waitLevel(ThreatLevel.NEUTRALISED, 10_000);
        mark("  NEUTRALISED");
        Thread.sleep(4000);
        JFrame blocked = launch("Honeytoken replay", null);
        waitText(blocked, "CONNECTION BLOCKED", 15_000);
        mark("  CONNECTION BLOCKED");
        Thread.sleep(4500);

        mark("7 counter-attack (lab range only)");
        caption("Counter-attack · lab range only", "The attacker's own technique, mirrored back through ScopeGuard");
        raise(frame("Specter — counter-watch"));
        raise(popup());
        press(popup(), "Counter-attack");
        JFrame counter = waitFrame("Mirage op shell — counter", 10_000);
        waitText(frame("Specter — counter-watch"), "INCOMING COUNTER-ATTACK", 15_000);
        mark("  INCOMING COUNTER-ATTACK");
        Thread.sleep(5000);
        caption("Simultaneous counter", "A bounded set of techniques, one thread each");
        raise(popup());
        press(popup(), "Simultaneous counter");
        waitPanes(counter, 4, 20_000);
        waitText(counter, "COUNTERS LAUNCHED", 15_000);
        mark("  counters split across threads");
        Thread.sleep(6000);

        mark("8 dossier + verify ledger");
        caption("Evidence", "The attacker's dossier and a hash-chained, signed ledger");
        JFrame dash = frame("Mirage Console");
        raise(dash);
        Thread.sleep(900);
        JTable table = onEdtGet(() -> find(dash, JTable.class));
        if (table != null && onEdtGet(table::getRowCount) > 0) {
            Point2D cell = onEdtGet(() -> {
                Rectangle r = table.getCellRect(0, 1, true);
                return toCanvas(table, r.x + r.width / 2.0, r.y + r.height / 2.0);
            });
            moveTo(cell.getX(), cell.getY(), null);
            Thread.sleep(120);
            ripple();
            SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));
            mark("  attacker dossier");
            Thread.sleep(4000);
        }
        press(dash, "Verify ledger");
        Thread.sleep(1200);
        mark("  ledger verified");
        Thread.sleep(4000);

        mark("9 out-of-range target refused");
        caption("Safety boundary", "A target outside the lab range is refused");
        JFrame refused = launch("Recon scan", "8.8.8.8");
        waitText(refused, "REFUSED — OUTSIDE LAB RANGE", 10_000);
        mark("  REFUSED");
        Thread.sleep(6000);
    }

    // ------------------------------------------------------------------ layout and paint order

    private static void place(Window w) {
        Rectangle slot = slotFor(w);
        if (slot == null || !w.isDisplayable()) {
            return;
        }
        Insets in = w.getInsets();
        Rectangle want = new Rectangle(slot.x, slot.y, slot.width + in.left + in.right,
                slot.height - TITLE + in.top + in.bottom);
        if (!want.equals(w.getBounds())) {
            w.setBounds(want);
            w.validate();
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

    /** Put {@code w} on top of the paint order (EDT only). */
    private static void raise(Window w) {
        if (w == null) {
            return;
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            raiseOnEdt(w);
            sleepQuietly(150);
            return;
        }
        place(w);
        Z.remove(w);
        Z.add(w);
    }

    private static void raiseOnEdt(Window w) {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                raise(w);
            } else {
                SwingUtilities.invokeAndWait(() -> raise(w));
            }
        } catch (Exception ignored) {
            // the paint order is cosmetic
        }
    }

    /** Where the window's root pane is drawn on the canvas. */
    private static Point2D contentOrigin(Window w) {
        Rectangle slot = slotFor(w);
        if (slot != null) {
            return new Point2D.Double(slot.x, slot.y + TITLE);
        }
        return root(w).getLocationOnScreen();
    }

    /** Canvas offset to add to a screen point inside {@code w}. */
    private static Point2D offsetFor(Window w) {
        if (!(w instanceof Frame) && w.getOwner() != null) {
            return offsetFor(w.getOwner()); // a heavyweight popup moves with its owner
        }
        Point2D o = contentOrigin(w);
        Point s = root(w).getLocationOnScreen();
        return new Point2D.Double(o.getX() - s.x, o.getY() - s.y);
    }

    /** A point in {@code c}'s own coordinates, on the canvas (EDT only). */
    private static Point2D toCanvas(Component c, double x, double y) {
        Window w = c instanceof Window cw ? cw : SwingUtilities.getWindowAncestor(c);
        Point s = c.getLocationOnScreen();
        Point2D off = offsetFor(w);
        return new Point2D.Double(s.x + x + off.getX(), s.y + y + off.getY());
    }

    private static JComponent root(Window w) {
        return ((RootPaneContainer) w).getRootPane();
    }

    // ------------------------------------------------------------------ the recorder

    private static Thread startRecorder(String ffmpeg, File out) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                "-f", "rawvideo", "-pix_fmt", "bgr0", "-s", OUT_W + "x" + OUT_H, "-r", String.valueOf(FPS),
                // the lightest encoder setting, so composing keeps the CPU it needs;
                // the file is large but goes through the editor anyway
                "-i", "-", "-c:v", "libx264", "-preset", "ultrafast", "-crf", "12", "-threads", "4",
                "-pix_fmt", "yuv420p",
                "-movflags", "+faststart", out.getAbsolutePath());
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process p = pb.start();
        int[] pixels = ((DataBufferInt) frame.getRaster().getDataBuffer()).getData();
        // Composing and writing overlap: the recorder composes a frame, converts it into a free
        // buffer and queues it; the writer thread sends queued buffers to ffmpeg.
        record Out(ByteBuffer buf, int times) { }
        java.util.concurrent.BlockingQueue<ByteBuffer> free = new java.util.concurrent.ArrayBlockingQueue<>(3);
        java.util.concurrent.BlockingQueue<Out> queued = new java.util.concurrent.ArrayBlockingQueue<>(3);
        for (int i = 0; i < 3; i++) {
            // TYPE_INT_RGB is 0x00RRGGBB; little-endian that is the bytes B, G, R, 0 = ffmpeg's bgr0
            free.add(ByteBuffer.allocate(pixels.length * 4).order(ByteOrder.LITTLE_ENDIAN));
        }
        Out end = new Out(null, 0);
        Thread writer = new Thread(() -> {
            try (OutputStream os = p.getOutputStream()) {
                while (true) {
                    Out o = queued.take();
                    if (o == end) {
                        break;
                    }
                    long w0 = System.nanoTime();
                    for (int i = 0; i < o.times(); i++) {
                        os.write(o.buf().array());
                        written++;
                    }
                    writeNanos += System.nanoTime() - w0;
                    free.put(o.buf());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "ffmpeg-writer");
        writer.start();
        recording = true;
        recStart = System.nanoTime();
        Thread t = new Thread(() -> {
            try {
                long n = 0;
                long step = 1_000_000_000L / FPS;
                while (recording) {
                    long due = recStart + n * step;
                    long wait = due - System.nanoTime();
                    if (wait > 0) {
                        Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
                    }
                    long c0 = System.nanoTime();
                    SwingUtilities.invokeAndWait(DemoVideo::compose);
                    composeNanos += System.nanoTime() - c0;
                    composed++;
                    ByteBuffer buf = free.take();
                    buf.clear();
                    buf.asIntBuffer().put(pixels);
                    // keep video time = wall time: repeat the frame if composing fell behind
                    int times = 0;
                    do {
                        times++;
                        n++;
                    } while (recStart + n * step + step < System.nanoTime());
                    queued.put(new Out(buf, times));
                }
                queued.put(end);
                writer.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                p.waitFor();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "recorder");
        t.start();
        return t;
    }

    private static void compose() {
        long now = System.nanoTime();
        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(SCALE, SCALE);
        AffineTransform base = g.getTransform();

        Card c = card;
        boolean fullCard = c != null && cardAlpha(c, now) >= 1f;
        if (!fullCard) {
            g.setTransform(new AffineTransform());
            g.drawImage(backdropImage, 0, 0, null);
            g.setTransform(base);
            for (Window w : new ArrayList<>(Z)) {
                if (w instanceof ThreatPopup p) {
                    ThreatLevel was = LAST_LEVEL.put(p, p.level());
                    if (was != null && was != p.level()) {
                        Z.remove(p);
                        Z.add(p); // the popup raises itself when it escalates
                    }
                }
            }
            SECTION[0] += System.nanoTime() - now;
            long s1 = System.nanoTime();
            List<Window> order = new ArrayList<>(Z);
            for (int i = 0; i < order.size(); i++) {
                Window w = order.get(i);
                if (w.isShowing() && !covered(w, order.subList(i + 1, order.size()))) {
                    paintWindow(g, w);
                    g.setTransform(base);
                }
            }
            SECTION[1] += System.nanoTime() - s1;
            long s2 = System.nanoTime();
            for (Window w : Window.getWindows()) {
                if (!(w instanceof Frame) && w.isShowing() && w instanceof RootPaneContainer && w.getOwner() != null) {
                    paintPopupWindow(g, w);
                    g.setTransform(base);
                }
            }
            SECTION[2] += System.nanoTime() - s2;
            long s3 = System.nanoTime();
            paintCaption(g, now);
            paintRipples(g, now);
            paintCursor(g);
            SECTION[3] += System.nanoTime() - s3;
        }
        long s4 = System.nanoTime();
        if (c != null) {
            paintCard(g, c, now);
        }
        SECTION[4] += System.nanoTime() - s4;
        g.dispose();
        bodyNanos += System.nanoTime() - now;
    }

    private static long bodyNanos;
    private static long writeNanos;
    /** Inside paintWindow: chrome, contents, copy. */
    private static final long[] PW = new long[3];
    /** Time spent per part of a frame: backdrop, windows, popups, overlays, cards. */
    private static final long[] SECTION = new long[5];

    /** True when windows drawn later hide this one completely. */
    private static boolean covered(Window w, List<Window> above) {
        Rectangle mine = slotFor(w);
        if (mine == null) {
            return false;
        }
        java.awt.geom.Area cover = new java.awt.geom.Area();
        for (Window a : above) {
            Rectangle r = slotFor(a);
            if (r != null && a.isShowing()) {
                // slots meet with a gap of 12 between them; count the gap as covered
                cover.add(new java.awt.geom.Area(new Rectangle(r.x, r.y, r.width, r.height + 12)));
            }
        }
        java.awt.geom.Area left = new java.awt.geom.Area(mine);
        left.subtract(cover);
        return left.isEmpty();
    }

    private static void paintWindow(Graphics2D g, Window w) {
        Point2D o = contentOrigin(w);
        JComponent rp = root(w);
        String title = w instanceof Frame f && f.getTitle() != null ? f.getTitle() : "";
        long p0 = System.nanoTime();
        AffineTransform t = g.getTransform();
        g.setTransform(new AffineTransform()); // everything below is in video pixels
        BufferedImage bar = titleBar(title, rp.getWidth());
        BufferedImage img = contents(w, rp);
        int x = (int) Math.round(o.getX() * SCALE);
        int cy = (int) Math.round(o.getY() * SCALE);
        int y = cy - bar.getHeight();
        int width = img.getWidth();
        int height = img.getHeight() + bar.getHeight();
        // soft shadow: thin strips round the window (filling under it would blend every pixel)
        for (int i = 9; i >= 1; i--) {
            g.setColor(SHADOW[i]);
            int sx = x - i;
            int sy = y - i + 4;
            int sw = width + 2 * i;
            int sh = height + 2 * i;
            g.fillRect(sx, sy, sw, 1);
            g.fillRect(sx, sy + sh - 1, sw, 1);
            g.fillRect(sx, sy + 1, 1, sh - 2);
            g.fillRect(sx + sw - 1, sy + 1, 1, sh - 2);
        }
        g.drawImage(bar, x, y, null);
        long p1 = System.nanoTime();
        g.drawImage(img, x, cy, null);
        g.setColor(BORDER);
        g.drawRect(x, y, width - 1, height - 1);
        long p3 = System.nanoTime();
        g.setTransform(t);
        PW[0] += p1 - p0;
        PW[2] += p3 - p1;
    }

    private static final Color BORDER = new Color(0x8A, 0x8F, 0x98);
    private static final Color[] SHADOW = new Color[10];

    static {
        for (int i = 1; i < SHADOW.length; i++) {
            SHADOW[i] = new Color(0, 0, 0, 12 + (9 - i) * 8);
        }
    }

    private static final Map<String, BufferedImage> BARS = new HashMap<>();

    /** A Windows 11 style title bar, drawn once per title and width, at video resolution. */
    private static BufferedImage titleBar(String title, int width) {
        return BARS.computeIfAbsent(width + "|" + title, k -> {
            BufferedImage b = new BufferedImage((int) Math.ceil(width * SCALE), (int) Math.round(TITLE * SCALE),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = b.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.scale(SCALE, SCALE);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width + 1, TITLE + 1);
            g.setColor(new Color(0x1F, 0x1F, 0x1F));
            g.setFont(TITLE_FONT);
            FontMetrics fm = g.getFontMetrics();
            String s = title;
            int room = width - 12 - 150;
            while (!s.isEmpty() && fm.stringWidth(s) > room) {
                s = s.substring(0, s.length() - 1);
            }
            g.drawString(s, 12, (TITLE + fm.getAscent() - fm.getDescent()) / 2f);
            g.setStroke(new BasicStroke(1f));
            int gx = width - 46 * 3;
            int cy = TITLE / 2;
            g.drawLine(gx + 18, cy, gx + 28, cy);
            g.drawRect(gx + 46 + 18, cy - 5, 10, 10);
            g.drawLine(gx + 92 + 18, cy - 5, gx + 92 + 28, cy + 5);
            g.drawLine(gx + 92 + 18, cy + 5, gx + 92 + 28, cy - 5);
            g.dispose();
            return b;
        });
    }

    /** The window's contents at video resolution, repainting only what changed since last frame. */
    private static BufferedImage contents(Window w, JComponent rp) {
        int pw = (int) Math.ceil(rp.getWidth() * SCALE);
        int ph = (int) Math.ceil(rp.getHeight() * SCALE);
        Rectangle all = new Rectangle(0, 0, rp.getWidth(), rp.getHeight());
        Rectangle d;
        synchronized (DIRTY) {
            d = DIRTY.remove(w);
        }
        BufferedImage img = CACHE.get(w);
        long now = System.nanoTime();
        if (img == null || img.getWidth() != pw || img.getHeight() != ph) {
            img = new BufferedImage(Math.max(1, pw), Math.max(1, ph), BufferedImage.TYPE_INT_RGB);
            CACHE.put(w, img);
            d = all;
        }
        // a full repaint now and then, in case a change slipped past the repaint manager
        if (now - LAST_FULL.getOrDefault(w, 0L) > 3_000_000_000L) {
            d = all;
        }
        if (d == all) {
            LAST_FULL.put(w, now);
        }
        if (d != null) {
            Rectangle clip = new Rectangle(d.x - 2, d.y - 2, d.width + 4, d.height + 4).intersection(all);
            if (!clip.isEmpty()) {
                long p0 = System.nanoTime();
                Graphics2D cg = img.createGraphics();
                cg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                cg.scale(SCALE, SCALE);
                cg.clip(clip);
                try {
                    rp.printAll(cg);
                } catch (RuntimeException ignored) {
                    // a window changing under us: the next frame will have it
                } finally {
                    cg.dispose();
                }
                String name = w instanceof Frame f ? f.getTitle() : w.getClass().getSimpleName();
                PRINT_NANOS.merge(name.length() > 40 ? name.substring(0, 40) : name, System.nanoTime() - p0, Long::sum);
            }
        }
        return img;
    }

    /** Records which part of which window Swing is about to repaint. */
    private static final class Tracker extends RepaintManager {
        @Override
        public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
            super.addDirtyRegion(c, x, y, w, h);
            dirty(c, x, y, w, h);
        }

        @Override
        public void addDirtyRegion(Window window, int x, int y, int w, int h) {
            super.addDirtyRegion(window, x, y, w, h);
            dirty(window, 0, 0, Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4);
        }

        @Override
        public void markCompletelyDirty(JComponent c) {
            super.markCompletelyDirty(c);
            dirty(c, 0, 0, c.getWidth(), c.getHeight());
        }
    }

    private static void dirty(Component c, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        Window win = c instanceof Window cw ? cw : SwingUtilities.getWindowAncestor(c);
        if (!(win instanceof RootPaneContainer rpc)) {
            return;
        }
        Rectangle r;
        try {
            Point p = c instanceof Window ? new Point(0, 0) : SwingUtilities.convertPoint(c, x, y, rpc.getRootPane());
            r = new Rectangle(p.x, p.y, w, h);
        } catch (RuntimeException e) {
            r = new Rectangle(0, 0, Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4);
        }
        synchronized (DIRTY) {
            DIRTY.merge(win, r, Rectangle::union);
        }
    }

    private static void paintPopupWindow(Graphics2D g, Window w) {
        try {
            Point2D p = toCanvas(w, 0, 0);
            JComponent rp = root(w);
            Graphics2D cg = (Graphics2D) g.create((int) p.getX(), (int) p.getY(), rp.getWidth(), rp.getHeight());
            rp.printAll(cg);
            cg.dispose();
        } catch (RuntimeException ignored) {
            // the popup closed while we painted
        }
    }

    private static void paintCaption(Graphics2D g, long now) {
        Caption c = caption;
        if (c == null || now > c.endNanos()) {
            return;
        }
        double in = Math.min(1, (now - c.startNanos()) / 450e6);
        double out = Math.min(1, (c.endNanos() - now) / 450e6);
        double a = easeOut(Math.max(0, Math.min(in, out)));
        if (a <= 0) {
            return;
        }
        Font tf = CAPTION_TITLE;
        Font sf = CAPTION_SUB;
        FontMetrics tm = g.getFontMetrics(tf);
        FontMetrics sm = g.getFontMetrics(sf);
        int w = Math.max(tm.stringWidth(c.title()), sm.stringWidth(c.sub())) + 18 + 22 + 5;
        int h = 13 + tm.getHeight() + 4 + sm.getHeight() + 14;
        double x = 36;
        double y = H - 36 - h + (1 - a) * 12;
        Graphics2D cg = (Graphics2D) g.create();
        cg.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) a));
        cg.setStroke(new BasicStroke(1.2f));
        for (int i = 8; i >= 1; i--) {
            cg.setColor(new Color(0, 0, 0, 10 + (8 - i) * 7));
            cg.draw(new RoundRectangle2D.Double(x - i, y - i + 6, w + 2 * i - 1, h + 2 * i - 1, 10 + i, 10 + i));
        }
        cg.setColor(new Color(12, 22, 38, 242));
        cg.fill(new RoundRectangle2D.Double(x, y, w, h, 10, 10));
        cg.setColor(ACCENT);
        cg.fill(new RoundRectangle2D.Double(x, y, 5, h, 4, 4));
        cg.fillRect((int) x + 2, (int) y, 3, h);
        cg.setFont(tf);
        cg.setColor(Color.WHITE);
        cg.drawString(c.title(), (float) (x + 5 + 18), (float) (y + 13 + tm.getAscent()));
        cg.setFont(sf);
        cg.setColor(new Color(0xA9, 0xC2, 0xCE));
        cg.drawString(c.sub(), (float) (x + 5 + 18), (float) (y + 13 + tm.getHeight() + 4 + sm.getAscent()));
        cg.dispose();
    }

    private static void paintRipples(Graphics2D g, long now) {
        for (Ripple r : RIPPLES) {
            double t = Math.max(0, (now - r.startNanos()) / 500e6);
            if (t >= 1) {
                RIPPLES.remove(r);
                continue;
            }
            double e = easeOut(t);
            double rad = 18 * (0.3 + 0.9 * e);
            int fa = (int) (0.30 * 255 * (1 - t));
            int sa = (int) (0.65 * 255 * (1 - t));
            Ellipse2D circle = new Ellipse2D.Double(r.x() - rad, r.y() - rad, rad * 2, rad * 2);
            g.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), fa));
            g.fill(circle);
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), sa));
            g.draw(circle);
        }
    }

    private static void paintCursor(Graphics2D g) {
        Path2D.Double arrow = new Path2D.Double();
        arrow.moveTo(3, 2);
        arrow.lineTo(3, 21);
        arrow.lineTo(8.2, 16.4);
        arrow.lineTo(11.6, 24);
        arrow.lineTo(15, 22.5);
        arrow.lineTo(11.7, 15);
        arrow.lineTo(18.5, 15);
        arrow.closePath();
        Graphics2D cg = (Graphics2D) g.create();
        cg.translate(curX - 3, curY - 2);
        cg.setColor(new Color(0, 0, 0, 40));
        cg.translate(1, 1.5);
        cg.fill(arrow);
        cg.translate(-1, -1.5);
        cg.setColor(Color.BLACK);
        cg.fill(arrow);
        cg.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        cg.setColor(Color.WHITE);
        cg.draw(arrow);
        cg.dispose();
    }

    private static float cardAlpha(Card c, long now) {
        double in = c.closing() ? (now - c.startNanos()) / 600e6 : 1;
        double out = (c.endNanos() - now) / 600e6;
        return (float) Math.max(0, Math.min(1, Math.min(in, out)));
    }

    private static void paintCard(Graphics2D g, Card c, long now) {
        float a = cardAlpha(c, now);
        if (a <= 0) {
            return;
        }
        Graphics2D cg = (Graphics2D) g.create();
        cg.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, a));
        cg.setPaint(new GradientPaint(0, 0, new Color(0x0A, 0x1C, 0x2A), W, H, new Color(0x06, 0x0E, 0x18)));
        cg.fillRect(0, 0, W, H);
        int x = 96;
        // badge
        cg.setColor(ACCENT);
        cg.fill(new RoundRectangle2D.Double(x, 236, 26, 26, 6, 6));
        cg.setColor(new Color(0x06, 0x0E, 0x18));
        cg.setFont(new Font("Segoe UI", Font.BOLD, 16));
        cg.drawString("M", x + 6, 255);
        cg.setColor(Color.WHITE);
        cg.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 16));
        cg.drawString("MIRAGE", x + 36, 255);
        if (!c.closing()) {
            spaced(cg, "OBJECT ORIENTED DESIGN AND PROGRAMMING-II LABORATORY · ICE-2206", x, 318,
                    new Font("Segoe UI Semibold", Font.PLAIN, 12), new Color(0x8F, 0xA6, 0xB8));
            cg.setColor(Color.WHITE);
            cg.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 42));
            cg.drawString("Mirage: Deception-Based Intrusion", x, 374);
            cg.drawString("Detection and Active Defence", x, 426);
            cg.setColor(new Color(0xC9, 0xD6, 0xE0));
            cg.setFont(new Font("Segoe UI", Font.PLAIN, 22));
            cg.drawString("Decoys catch the attacker. The operator answers.", x, 470);
            cg.setColor(new Color(0xE6, 0xEC, 0xF1));
            cg.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            cg.drawString("Nobo Ishraque · Department of ICT · Bangladesh University of Professionals", x, 536);
            cg.setColor(new Color(0x83, 0x96, 0xA6));
            cg.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            cg.drawString("Recorded on the running system: Java · Swing · Console, Sensor and Specter on one laptop (127.0.0.1)",
                    x, 560);
        } else {
            spaced(cg, "WHAT HELD AT EVERY STEP", x, 318,
                    new Font("Segoe UI Semibold", Font.PLAIN, 12), new Color(0x8F, 0xA6, 0xB8));
            cg.setColor(Color.WHITE);
            cg.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 42));
            cg.drawString("Every response goes through ScopeGuard.", x, 374);
            cg.setColor(new Color(0xC9, 0xD6, 0xE0));
            cg.setFont(new Font("Segoe UI", Font.PLAIN, 22));
            cg.drawString("Nothing leaves the lab range.", x, 418);
            cg.setColor(new Color(0xE6, 0xEC, 0xF1));
            cg.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            cg.drawString("3 decoys  ·  7 detection rules  ·  hash-chained, signed ledger  ·  299 tests", x, 490);
        }
        cg.dispose();
    }

    private static void spaced(Graphics2D g, String s, int x, int y, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        float cx = x;
        for (char ch : s.toCharArray()) {
            g.drawString(String.valueOf(ch), cx, y);
            cx += fm.charWidth(ch) + 1.6f;
        }
    }

    // ------------------------------------------------------------------ the pointer

    private interface Step {
        void at(double x, double y);
    }

    /**
     * Glide the pointer to (x, y) the way a hand does: a slightly curved path, quick in the
     * middle and slow at both ends (minimum-jerk timing), longer moves taking a little longer.
     */
    private static void moveTo(double x, double y, Step onStep) throws Exception {
        unhover();
        double sx = curX;
        double sy = curY;
        double dx = x - sx;
        double dy = y - sy;
        double dist = Math.hypot(dx, dy);
        if (dist < 2) {
            curX = x;
            curY = y;
            return;
        }
        double ms = Math.min(720, 230 + dist * 0.33);
        double bend = dist * (0.04 + RND.nextDouble() * 0.06) * (RND.nextBoolean() ? 1 : -1);
        double qx = sx + dx / 2 - dy / dist * bend;
        double qy = sy + dy / 2 + dx / dist * bend;
        long start = System.nanoTime();
        while (true) {
            double t = (System.nanoTime() - start) / 1e6 / ms;
            if (t >= 1) {
                break;
            }
            double e = t * t * t * (10 - 15 * t + 6 * t * t);
            double u = 1 - e;
            curX = u * u * sx + 2 * u * e * qx + e * e * x;
            curY = u * u * sy + 2 * u * e * qy + e * e * y;
            if (onStep != null) {
                onStep.at(curX, curY);
            }
            Thread.sleep(6);
        }
        curX = x;
        curY = y;
        if (onStep != null) {
            onStep.at(x, y);
        }
    }

    private static void ripple() {
        RIPPLES.add(new Ripple(curX, curY, System.nanoTime()));
    }

    private static void unhover() throws Exception {
        AbstractButton h = hovered;
        if (h != null) {
            hovered = null;
            SwingUtilities.invokeAndWait(() -> h.getModel().setRollover(false));
        }
    }

    /** Glide to a spot inside {@code c} (a little off its exact middle, as people click). */
    private static void glideInto(Component c) throws Exception {
        Point2D p = onEdtGet(() -> {
            double jx = Math.min(10, c.getWidth() / 5.0) * (RND.nextDouble() * 2 - 1);
            double jy = Math.min(3, c.getHeight() / 6.0) * (RND.nextDouble() * 2 - 1);
            return toCanvas(c, c.getWidth() / 2.0 + jx, c.getHeight() / 2.0 + jy);
        });
        moveTo(p.getX(), p.getY(), null);
    }

    /** Glide to the button, hover, press and release it (the pressed look shows). */
    private static void click(AbstractButton b) throws Exception {
        glideInto(b);
        hovered = b;
        SwingUtilities.invokeAndWait(() -> b.getModel().setRollover(true));
        Thread.sleep(140);
        ripple();
        ButtonModel m = b.getModel();
        SwingUtilities.invokeAndWait(() -> {
            m.setArmed(true);
            m.setPressed(true);
        });
        Thread.sleep(110);
        SwingUtilities.invokeAndWait(() -> {
            m.setPressed(false); // fires the action
            m.setArmed(false);
        });
        Thread.sleep(250);
    }

    private static void press(Container c, String text) throws Exception {
        AbstractButton b = onEdtGet(() -> button(c, text));
        if (b == null) {
            throw new IllegalStateException("no button '" + text + "'");
        }
        click(b);
    }

    /** Open the combo box, run the pointer down its list and click the item. */
    private static void choose(JComboBox<?> box, String item) throws Exception {
        glideInto(box);
        Thread.sleep(120);
        ripple();
        SwingUtilities.invokeAndWait(box::showPopup);
        Thread.sleep(320);
        JList<?> list = onEdtGet(() -> box.getUI().getAccessibleChild(box, 0) instanceof ComboPopup p ? p.getList() : null);
        int index = onEdtGet(() -> {
            for (int i = 0; i < box.getItemCount(); i++) {
                if (item.equals(String.valueOf(box.getItemAt(i)))) {
                    return i;
                }
            }
            return -1;
        });
        if (list == null || index < 0 || !onEdtGet(list::isShowing)) {
            SwingUtilities.invokeAndWait(() -> {
                box.hidePopup();
                box.setSelectedItem(item);
            });
            return;
        }
        Point2D target = onEdtGet(() -> {
            Rectangle r = list.getCellBounds(index, index);
            return toCanvas(list, r.x + Math.min(60, r.width / 3.0), r.y + r.height / 2.0);
        });
        moveTo(target.getX(), target.getY(), (x, y) -> SwingUtilities.invokeLater(() -> {
            if (!list.isShowing()) {
                return;
            }
            Point2D o = toCanvas(list, 0, 0);
            int row = list.locationToIndex(new Point((int) (x - o.getX()), (int) (y - o.getY())));
            Rectangle r = row >= 0 ? list.getCellBounds(row, row) : null;
            if (r != null && r.contains((int) (x - o.getX()), (int) (y - o.getY()))) {
                list.setSelectedIndex(row);
            }
        }));
        Thread.sleep(160);
        ripple();
        SwingUtilities.invokeAndWait(() -> {
            list.setSelectedIndex(index);
            box.setSelectedIndex(index);
            box.hidePopup();
        });
        Thread.sleep(300);
    }

    /** Click into the field, delete what is there, type the new text at a person's pace. */
    private static void typeInto(JTextField field, String text) throws Exception {
        glideInto(field);
        Thread.sleep(120);
        ripple();
        SwingUtilities.invokeAndWait(() -> {
            field.requestFocusInWindow();
            field.setCaretPosition(field.getText().length());
        });
        Thread.sleep(250);
        while (!onEdtGet(field::getText).isEmpty()) {
            SwingUtilities.invokeAndWait(() -> field.setText(field.getText().substring(0, field.getText().length() - 1)));
            Thread.sleep(45);
        }
        Thread.sleep(220);
        for (char ch : text.toCharArray()) {
            SwingUtilities.invokeAndWait(() -> field.setText(field.getText() + ch));
            Thread.sleep(70 + RND.nextInt(90));
        }
        Thread.sleep(350);
    }

    /** Pick a campaign in Specter (optionally retype the target) and press Launch. */
    private static JFrame launch(String campaign, String host) throws Exception {
        JFrame specter = frame("Specter — attack simulator");
        raise(specter);
        Set<Frame> before = new LinkedHashSet<>(List.of(Frame.getFrames()));
        if (host != null) {
            typeInto(onEdtGet(() -> find(specter, JTextField.class)), host);
        }
        JComboBox<?> box = onEdtGet(() -> find(specter, JComboBox.class));
        if (!campaign.equals(String.valueOf(onEdtGet(box::getSelectedItem)))) {
            choose(box, campaign);
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
        raise(created[0]);
        return created[0];
    }

    private static void caption(String title, String sub) {
        long now = System.nanoTime();
        caption = new Caption(title, sub, now, now + 3_800_000_000L);
    }

    // ------------------------------------------------------------------ lookups and waits

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
        raise(f);
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

    // ------------------------------------------------------------------ output

    private static double easeOut(double t) {
        return 1 - Math.pow(1 - t, 3);
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A timeline line (video time), for the voice-over; plus a still when shots/ exists. */
    private static void mark(String what) {
        String line = String.format("%6.1fs  %s", (System.nanoTime() - recStart) / 1e9, what);
        System.out.println("@@ " + line);
        if (new File("shots").isDirectory() && frame != null) {
            try {
                BufferedImage small = onEdtGet(() -> {
                    BufferedImage s = new BufferedImage(OUT_W / 2, OUT_H / 2, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = s.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(frame, 0, 0, s.getWidth(), s.getHeight(), null);
                    g.dispose();
                    return s;
                });
                javax.imageio.ImageIO.write(small, "png", new File("shots", String.format("%02d.png", shotNo++)));
            } catch (Exception ignored) {
                // snapshots are for checking only
            }
        }
        try {
            Files.writeString(new File("timeline.txt").toPath(), line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
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
