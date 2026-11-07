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
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    private final JPanel splitContainer = new JPanel(new GridLayout(1, 0, 4, 0));
    private final Map<String, JTextPane> threadPanes = new LinkedHashMap<>();
    private final JCheckBox splitToggle = new JCheckBox("Split threads");
    private final JSlider speed = new JSlider(1, 20, 3);
    private final JLabel status = new JLabel(" ");
    private final JScrollPane streamScroll;
    private final JScrollPane splitScroll;
    private final Timer timer;
    private int lastLaneCount = -1;

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
            splitContainer.add(new JScrollPane(pane));
            splitContainer.revalidate();
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
        if (lanes.size() == lastLaneCount) {
            // repaint states without rebuilding when the lane set is unchanged
            strip.repaint();
            return;
        }
        lastLaneCount = lanes.size();
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
        add(splitToggle.isSelected() ? splitScroll : streamScroll, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void clearStream() {
        stream.setText("");
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
}
