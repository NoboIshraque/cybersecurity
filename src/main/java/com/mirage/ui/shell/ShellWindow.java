package com.mirage.ui.shell;

import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A terminal-style process shell. Trace lines (already filtered for this shell)
 * are fed in from the bus; a timer drains them a few at a time so the output
 * "types out" and stays readable even for a sub-second burst. Each line is tagged
 * and coloured by the thread that produced it, there is a live thread strip on
 * top, banners are drawn large, and a "split threads" toggle shows one pane per
 * concurrently running thread. A speed slider paces the display; Save writes the
 * log to a file.
 *
 * <p>This is a plain {@link javax.swing.JPanel}; the owning window embeds it. All
 * decision logic lives in the unit-tested {@link ShellModel}; this class only draws.
 */
public final class ShellWindow extends JPanel {

    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    private final ShellModel model = new ShellModel();
    private final ShellTheme theme;
    private final Predicate<TraceEvent> filter;

    private final JTextPane stream = new JTextPane();
    private final JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
    private final SplitGrid splitContainer = new SplitGrid();
    private final Map<String, JTextPane> threadPanes = new LinkedHashMap<>();
    /**
     * FB-07: the lines shown so far (the last {@link ShellDocuments#MAX_LINES}), so turning on
     * split view, by hand or by a concurrent launch, gives every thread its pane with its lines.
     */
    private final ArrayDeque<ShellModel.Line> history = new ArrayDeque<>();
    private final JCheckBox splitToggle = new JCheckBox("Split threads");
    private final JSlider speed = new JSlider(1, 20, 3);
    private final JLabel status = new JLabel(" ");
    private final JScrollPane streamScroll;
    private final JScrollPane splitScroll;
    private final Timer timer;
    /** The lanes (thread + state) the strip shows now; rebuilt when any of them changes (B-16). */
    private List<ShellModel.Lane> shownLanes = List.of();

    public ShellWindow(ShellTheme theme, Predicate<TraceEvent> filter) {
        super(new BorderLayout(0, 0));
        this.theme = theme;
        this.filter = filter == null ? e -> true : filter;

        stream.setEditable(false);
        stream.setBackground(theme.background);
        stream.setForeground(theme.foreground);
        stream.setFont(MONO);
        stream.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        streamScroll = new JScrollPane(stream);
        streamScroll.setBorder(BorderFactory.createEmptyBorder());

        splitContainer.setBackground(theme.background);
        splitScroll = new JScrollPane(splitContainer);
        splitScroll.setBorder(BorderFactory.createEmptyBorder());

        strip.setBackground(theme.background.darker());
        strip.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

        add(strip, BorderLayout.NORTH);
        add(streamScroll, BorderLayout.CENTER);
        add(buildControls(), BorderLayout.SOUTH);

        timer = new Timer(45, e -> tick());
        timer.start();
    }

    private JPanel buildControls() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        controls.setBackground(theme.background.darker());

        JLabel speedLabel = new JLabel("Speed");
        speedLabel.setForeground(theme.foreground);
        speed.setOpaque(false);
        speed.setPreferredSize(new Dimension(120, 24));

        splitToggle.setForeground(theme.foreground);
        splitToggle.setOpaque(false);
        splitToggle.addActionListener(e -> switchView());

        JButton save = new JButton("Save log");
        save.addActionListener(e -> saveLog());

        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> clearStream());

        status.setForeground(theme.foreground);

        controls.add(speedLabel);
        controls.add(speed);
        controls.add(splitToggle);
        controls.add(save);
        controls.add(clear);
        controls.add(Box.createHorizontalStrut(12));
        controls.add(status);
        return controls;
    }

    /** Register this on the TraceBus: {@code bus.subscribe(shell::onTrace)}. */
    public void onTrace(TraceEvent event) {
        if (filter.test(event)) {
            model.add(event); // thread-safe; drained on the EDT by the timer
        }
    }

    private void tick() {
        // B-17: the slider's pace for short bursts, faster when behind, so the shell stays live
        List<ShellModel.Line> due = model.pollPaced(speed.getValue());
        for (ShellModel.Line line : due) {
            remember(line);
            appendToStream(line);
            if (splitToggle.isSelected()) {
                appendToThreadPane(line);
            }
        }
        if (!due.isEmpty()) {
            trimToLastLines();
            refreshStrip();
            status.setText("lines: " + model.totalLines()
                    + "   threads: " + model.lanes().size()
                    + (model.hasPending() ? "   (streaming…)" : ""));
        }
    }

    private void remember(ShellModel.Line line) {
        history.addLast(line);
        if (history.size() > ShellDocuments.MAX_LINES) {
            history.removeFirst();
        }
    }

    /** Keep each pane to its last {@link ShellDocuments#MAX_LINES} lines (B-17). */
    private void trimToLastLines() {
        ShellDocuments.trimHead(stream.getStyledDocument(), ShellDocuments.MAX_LINES);
        for (JTextPane pane : threadPanes.values()) {
            ShellDocuments.trimHead(pane.getStyledDocument(), ShellDocuments.MAX_LINES);
        }
    }

    private void appendToStream(ShellModel.Line line) {
        StyledDocument doc = stream.getStyledDocument();
        try {
            if (line.banner()) {
                appendBanner(doc, line.text());
                return;
            }
            SimpleAttributeSet tag = attr(theme.palette[line.colorIndex() % theme.palette.length], false);
            SimpleAttributeSet body = attr(colorFor(line.level()), line.level() == TraceLevel.COMMAND);
            String prompt = line.level() == TraceLevel.COMMAND ? "$ " : "";
            doc.insertString(doc.getLength(), pad("[" + line.thread() + "]", 22), tag);
            doc.insertString(doc.getLength(), prompt + line.text() + "\n", body);
            stream.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {
            // never happens at the document end
        }
    }

    private void appendBanner(StyledDocument doc, String text) throws BadLocationException {
        SimpleAttributeSet bar = new SimpleAttributeSet();
        StyleConstants.setForeground(bar, theme.bannerFg);
        StyleConstants.setBackground(bar, theme.bannerBg);
        StyleConstants.setBold(bar, true);
        StyleConstants.setFontSize(bar, 18);
        StyleConstants.setFontFamily(bar, Font.MONOSPACED);
        String framed = "   " + text + "   ";
        doc.insertString(doc.getLength(), "\n", bar);
        doc.insertString(doc.getLength(), center(framed) + "\n", bar);
        doc.insertString(doc.getLength(), "\n", bar);
        stream.setCaretPosition(doc.getLength());
    }

    private void appendToThreadPane(ShellModel.Line line) {
        JTextPane pane = threadPanes.get(line.thread());
        if (pane == null) {
            pane = new JTextPane();
            pane.setEditable(false);
            pane.setBackground(theme.background);
            pane.setFont(MONO);
            pane.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(theme.palette[line.colorIndex() % theme.palette.length]),
                    line.thread()));
            threadPanes.put(line.thread(), pane);
            JScrollPane scroll = new JScrollPane(pane);
            scroll.setPreferredSize(new Dimension(SplitGrid.PANE_WIDTH, SplitGrid.PANE_HEIGHT));
            splitContainer.add(scroll);
            splitContainer.fitColumns();
        }
        StyledDocument doc = pane.getStyledDocument();
        try {
            SimpleAttributeSet body = attr(colorFor(line.level()), line.level() == TraceLevel.COMMAND);
            doc.insertString(doc.getLength(),
                    (line.level() == TraceLevel.COMMAND ? "$ " : "") + line.text() + "\n", body);
            pane.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {
            // end of document
        }
    }

    private void refreshStrip() {
        List<ShellModel.Lane> lanes = model.lanes();
        if (lanes.equals(shownLanes)) {
            // same threads in the same states: nothing to rebuild
            strip.repaint();
            return;
        }
        shownLanes = lanes;
        strip.removeAll();
        JLabel title = new JLabel("threads:");
        title.setForeground(theme.foreground);
        strip.add(title);
        for (ShellModel.Lane lane : lanes) {
            JLabel dot = new JLabel("● " + lane.thread() + stateMark(lane.state()));
            dot.setForeground(theme.palette[lane.colorIndex() % theme.palette.length]);
            dot.setFont(MONO);
            strip.add(dot);
        }
        strip.revalidate();
        strip.repaint();
    }

    private void switchView() {
        remove(streamScroll);
        remove(splitScroll);
        if (splitToggle.isSelected()) {
            rebuildThreadPanes();
        }
        add(splitToggle.isSelected() ? splitScroll : streamScroll, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /** FB-07: one pane per thread, filled with the lines already shown. */
    private void rebuildThreadPanes() {
        threadPanes.clear();
        splitContainer.removeAll();
        for (ShellModel.Line line : history) {
            appendToThreadPane(line);
        }
        splitContainer.fitColumns();
    }

    private void clearStream() {
        stream.setText("");
        history.clear();
        threadPanes.clear();
        splitContainer.removeAll();
        splitContainer.revalidate();
        splitContainer.repaint();
    }

    private void saveLog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("mirage-shell.log"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try (Writer w = Files.newBufferedWriter(chooser.getSelectedFile().toPath(), StandardCharsets.UTF_8)) {
            w.write(stream.getText());
        } catch (IOException e) {
            status.setText("save failed: " + e.getMessage());
        }
    }

    private SimpleAttributeSet attr(Color color, boolean bold) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, color);
        StyleConstants.setBold(a, bold);
        StyleConstants.setFontFamily(a, Font.MONOSPACED);
        StyleConstants.setFontSize(a, 13);
        return a;
    }

    private Color colorFor(TraceLevel level) {
        return switch (level) {
            case GOOD -> theme.good;
            case WARN -> theme.warn;
            case BAD -> theme.bad;
            case COMMAND -> theme.command;
            case BANNER -> theme.bannerFg;
            case INFO -> theme.info;
        };
    }

    /** The thread strip's lane labels as shown (for tests and the dry run). Call on the EDT. */
    public List<String> stripLabels() {
        List<String> out = new java.util.ArrayList<>();
        for (java.awt.Component c : strip.getComponents()) {
            if (c instanceof JLabel label && label.getText().startsWith("●")) {
                out.add(label.getText());
            }
        }
        return out;
    }

    private static String stateMark(ShellModel.State state) {
        return switch (state) {
            case RUNNING -> " •run";
            case WAITING -> " •wait";
            case DONE -> " •done";
        };
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s + " " : s + " ".repeat(width - s.length());
    }

    private static String center(String s) {
        int width = 72;
        if (s.length() >= width) {
            return s;
        }
        int left = (width - s.length()) / 2;
        return " ".repeat(left) + s;
    }

    /** Start in split-threads view (used for a concurrent launch). */
    public void enableSplit() {
        if (!splitToggle.isSelected()) {
            splitToggle.setSelected(true);
            switchView();
        }
    }

    /** Stop the paint timer; call when the owning window is disposed. */
    public void close() {
        timer.stop();
    }

    /** Exposed for the owning window / screenshots. */
    public ShellModel model() {
        return model;
    }

    /**
     * FB-03: the split view's panes in a grid that fits the window's width: up to three side by
     * side, then two rows (4 → 2×2, 5 → 3+2, 6 → 3×2, 7–8 → 4+4). Rows fill the height while
     * they fit; with many threads the view scrolls down, never sideways.
     */
    private static final class SplitGrid extends JPanel implements Scrollable {

        static final int PANE_WIDTH = 220;
        static final int PANE_HEIGHT = 140;
        private static final int GAP = 4;

        SplitGrid() {
            super(new GridLayout(1, 1, GAP, GAP));
        }

        /** Re-pick the columns for the number of panes now. */
        void fitColumns() {
            int n = Math.max(1, getComponentCount());
            int columns = n <= 3 ? n : (n + 1) / 2;
            setLayout(new GridLayout(0, columns, GAP, GAP));
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true; // never wider than the window: no sideways scrolling
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport view && view.getHeight() >= getPreferredSize().height;
        }
    }

    /** Tests: show every pending line now, as the timer would over the next ticks. Call on the EDT. */
    void drain() {
        while (model.hasPending()) {
            tick();
        }
        tick();
    }

    /** Tests: each thread pane's text, by thread. Call on the EDT. */
    Map<String, String> threadPaneTexts() {
        Map<String, String> out = new LinkedHashMap<>();
        threadPanes.forEach((thread, pane) -> out.put(thread, pane.getText()));
        return out;
    }

    /** Tests: the scroll pane that holds the split view. */
    JScrollPane splitView() {
        return splitScroll;
    }
}
