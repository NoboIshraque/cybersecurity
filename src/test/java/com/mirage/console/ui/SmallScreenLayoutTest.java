package com.mirage.console.ui;

import com.mirage.common.ScopeGuard;
import com.mirage.common.trace.TraceBacklog;
import com.mirage.common.trace.TraceBus;
import com.mirage.console.AlertLedger;
import com.mirage.console.AttackerDirectory;
import com.mirage.console.AutoResponse;
import com.mirage.console.CommandSink;
import com.mirage.console.ResponseController;
import com.mirage.ui.shell.ShellWindow;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * FB-04: below about 1100 px the dashboard and the threat popup lost controls. At 1024 px the
 * alert feed collapsed to ~240 px and the status line was cut at {@code Ledger:}; at 800 px the
 * Unblock button and the status tail were gone; at 640x420 the popup lost "Inspect in dashboard".
 * Every action must stay on screen, whole, at those sizes, and the ledger state must stay readable.
 */
class SmallScreenLayoutTest {

    private static final String[] DETAIL_BUTTONS = {
        "Tarpit", "Feed fake data", "Block", "Unblock", "Counter-attack", "Simultaneous counter"};
    private static final String[] DASHBOARD_BUTTONS = {"Auto-response", "Pipeline shell", "Verify ledger"};
    private static final String[] POPUP_BUTTONS = {
        "Tarpit", "Feed fake data", "Block", "Counter-attack", "Simultaneous counter",
        "Inspect in dashboard", "Save log", "Clear"};
    private static final String[] RESPONSE_NOTES = {"turn the attacker", "Every command"};

    // ------------------------------------------------------------- headless checks

    @Test
    void windowsKeepTheirSizeOnABigScreenAndShrinkToFitASmallOne() {
        Dimension dashboard = new Dimension(1120, 680);
        assertEquals(dashboard, WindowSizing.fit(dashboard, new Rectangle(0, 0, 1920, 1040)));
        assertEquals(new Dimension(1024, 680), WindowSizing.fit(dashboard, new Rectangle(0, 0, 1024, 728)));
        assertEquals(new Dimension(800, 560), WindowSizing.fit(dashboard, new Rectangle(0, 0, 800, 560)));
        assertEquals(new Dimension(640, 420), WindowSizing.fit(new Dimension(920, 600), new Rectangle(0, 0, 640, 420)));
    }

    /**
     * The detail panel's minimum width is what squeezed the feed (a JSplitPane honours it). At 800 px
     * each half of the split gets about 390 px, so the panel must be able to live in less than that.
     */
    @Test
    void theDetailPanelFitsHalfOfAn800PxWindow() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                AttackerDetailPanel detail = new AttackerDetailPanel(new AttackerDirectory(), (op, ip, r) -> { });
                showResponsesTab(detail);
                assertTrue(detail.getMinimumSize().width <= 360,
                        "minimum width " + detail.getMinimumSize().width + " px leaves the feed no room");
                for (Dimension size : new Dimension[]{new Dimension(480, 520), new Dimension(370, 380)}) {
                    detail.setSize(size);
                    layOutWithoutPeer(detail);
                    for (String text : DETAIL_BUTTONS) {
                        assertWhollyInside(only(detail, button(text)), detail, text + " at " + size.width + " px");
                    }
                    for (String note : RESPONSE_NOTES) {
                        assertReadable(only(detail, startsWith(note)), detail, "'" + note + "…' at " + size.width + " px");
                    }
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        rethrow(failure);
    }

    // ------------------------------------------------------------- real windows (need a display)

    @Test
    void theDashboardKeepsEveryControlAt1024x700() throws Exception {
        checkDashboard(1024, 700);
    }

    @Test
    void theDashboardKeepsEveryControlAt800x500() throws Exception {
        checkDashboard(800, 500);
    }

    @Test
    void thePopupKeepsEveryControlAt640x420() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            ThreatPopup popup = new ThreatPopup("127.0.0.1", new TraceBacklog(100),
                    (op, ip, r) -> r.accept("BLOCK -> 127.0.0.1 (result line)"), ip -> { });
            try {
                popup.setVisible(true); // opens at its own size, as on an escalation
                Container root = popup.getRootPane();
                ((AbstractButton) only(root, button("Block"))).doClick(); // a result line under the buttons
                popup.validate();
                popup.setSize(640, 420); // then the window is made smaller
                popup.validate();
                for (String text : POPUP_BUTTONS) {
                    assertWhollyInside(only(root, button(text)), root, "popup: " + text);
                }
                assertReadable(only(root, startsWith("BLOCK -> 127.0.0.1")), root, "popup: the result line");
                Component buttons = only(root, button("Inspect in dashboard")).getParent();
                Component result = only(root, startsWith("BLOCK -> 127.0.0.1"));
                assertTrue(result.getY() >= buttons.getY() + buttons.getHeight(),
                        "the result line is drawn over the buttons: " + result.getBounds() + " vs " + buttons.getBounds());
                ShellWindow shell = only(root, c -> c instanceof ShellWindow);
                assertTrue(shell.getHeight() >= 120, "the shell keeps a usable height: " + shell.getHeight());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                popup.dispose();
            }
        });
        rethrow(failure);
    }

    private static void checkDashboard(int width, int height) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Dashboard dash = new Dashboard(new AlertLedger(), () -> 1, new AttackerDirectory(),
                    new ResponseController(new ScopeGuard(List.of("127.0.0.1/32")), CommandSink.NONE), null,
                    new LiveOptions(new TraceBus(), new AutoResponse(false), "127.0.0.1:8443"));
            try {
                dash.setSize(1120, 680);
                dash.setVisible(true); // opens at full size ...
                showResponsesTab(dash);
                ((AbstractButton) only(dash.getRootPane(), button("Verify ledger"))).doClick(); // DEMO step 7
                dash.validate();
                dash.setSize(width, height); // ... then the window is made smaller
                dash.validate();
                String at = " at " + width + "x" + height;
                Container root = dash.getRootPane();
                for (String text : DETAIL_BUTTONS) {
                    assertWhollyInside(only(root, button(text)), root, text + at);
                }
                for (String text : DASHBOARD_BUTTONS) {
                    assertWhollyInside(only(root, button(text)), root, text + at);
                }
                for (String note : RESPONSE_NOTES) {
                    assertReadable(only(root, startsWith(note)), root, "'" + note + "…'" + at);
                }
                Component ledger = only(root, c -> c instanceof JLabel l && l.getText() != null
                        && l.getText().contains("Ledger:"));
                assertTrue(((JLabel) ledger).getText().contains("Ledger: verified"), ((JLabel) ledger).getText());
                assertReadable(ledger, root, "ledger state" + at);
                assertTrue(root.getComponentCount() > 0 && allText(root).contains("Alerts: 0"), "alert count shown");

                // The feed and the detail split the window: the pre-FB-04 layout gave the feed 560 of
                // 1120 px. 45% leaves room for the divider and borders and still gives each of the five
                // columns about 90 px at 1024, enough to read a value (it was ~240 px, 24%).
                JTable table = only(root, c -> c instanceof JTable);
                Component feed = SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
                int content = dash.getContentPane().getWidth();
                assertTrue(feed.getWidth() >= 0.45 * content,
                        "alert feed " + feed.getWidth() + " of " + content + " px" + at);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                dash.dispose();
            }
        });
        rethrow(failure);
    }

    // ------------------------------------------------------------- helpers

    private static void showResponsesTab(Container root) {
        JTabbedPane tabs = only(root, c -> c instanceof JTabbedPane);
        tabs.setSelectedIndex(tabs.indexOfTab("Responses"));
    }

    /** What validate() does when there is no native peer (headless): invalidate and lay out, top-down. */
    private static void layOutWithoutPeer(Component c) {
        for (int pass = 0; pass < 3; pass++) { // wrapped rows settle on the second pass
            invalidateAll(c);
            doLayoutAll(c);
        }
    }

    private static void invalidateAll(Component c) {
        c.invalidate();
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                invalidateAll(child);
            }
        }
    }

    private static void doLayoutAll(Component c) {
        if (c instanceof Container k) {
            k.doLayout();
            for (Component child : k.getComponents()) {
                doLayoutAll(child);
            }
        }
    }

    /** Visible, non-empty, and not clipped by any container between it and {@code root}. */
    private static void assertWhollyInside(Component c, Container root, String what) {
        assertTrue(c.isVisible() && c.getWidth() > 0 && c.getHeight() > 0, what + " is not shown: " + c.getBounds());
        Rectangle r = c.getBounds();
        for (Container p = c.getParent(); p != null; p = p.getParent()) {
            assertTrue(p == root || p.isVisible(), what + " sits in a hidden " + p.getClass().getSimpleName());
            assertTrue(r.x >= 0 && r.y >= 0 && r.x + r.width <= p.getWidth() && r.y + r.height <= p.getHeight(),
                    what + " is cut off by " + p.getClass().getSimpleName() + " " + p.getSize() + ": " + r);
            if (p == root) {
                return;
            }
            r.translate(p.getX(), p.getY());
        }
        fail(what + " is not inside the window");
    }

    /** Wholly inside, and given at least the room its whole text needs (no "…" truncation). */
    private static void assertReadable(Component c, Container root, String what) {
        assertWhollyInside(c, root, what);
        Dimension pref = c.getPreferredSize();
        if (c instanceof JLabel) {
            assertTrue(c.getWidth() >= pref.width, what + " is truncated: " + c.getWidth() + " < " + pref.width);
        }
        assertTrue(c.getHeight() >= pref.height, what + " is cut short: " + c.getHeight() + " < " + pref.height);
    }

    private static Predicate<Component> button(String text) {
        return c -> c instanceof AbstractButton b && text.equals(b.getText());
    }

    private static Predicate<Component> startsWith(String prefix) {
        return c -> (c instanceof JLabel l && l.getText() != null && l.getText().startsWith(prefix))
                || (c instanceof JTextComponent t && t.getText().startsWith(prefix));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> T only(Container root, Predicate<Component> match) {
        List<Component> found = new ArrayList<>();
        collect(root, match, found);
        assertEquals(1, found.size(), "expected exactly one match, found " + found.size());
        return (T) found.get(0);
    }

    private static void collect(Container c, Predicate<Component> match, List<Component> out) {
        for (Component child : c.getComponents()) {
            if (match.test(child)) {
                out.add(child);
            }
            if (child instanceof Container inner) {
                collect(inner, match, out);
            }
        }
    }

    private static String allText(Container c) {
        StringBuilder sb = new StringBuilder();
        collect(c, x -> x instanceof JLabel, new ArrayList<>() {
            @Override
            public boolean add(Component x) {
                sb.append(((JLabel) x).getText()).append('\n');
                return true;
            }
        });
        return sb.toString();
    }

    private static void rethrow(AtomicReference<Throwable> failure) throws Exception {
        Throwable t = failure.get();
        if (t instanceof Error e) {
            throw e;
        }
        if (t instanceof Exception e) {
            throw e;
        }
    }
}
