package com.mirage.console.ui;

import com.mirage.common.Severity;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The live network map: one node per source that has touched a decoy. A node
 * starts grey and turns from green through red as the worst alert raised against
 * that source escalates. Clicking a node selects that source.
 */
public final class NetworkMapPanel extends JPanel {

    /** One map node: a source, the worst severity seen for it (null = no alert yet), and its alert count. */
    public record Node(String sourceIp, Severity severity, int alertCount) { }

    private static final int W = 150, H = 50, GAP = 14, MARGIN = 12;

    private final transient List<Node> nodes = new ArrayList<>();
    private final transient Map<Node, Rectangle> hitboxes = new LinkedHashMap<>();
    private final transient Consumer<String> onSelect;
    private String selected;

    public NetworkMapPanel(Consumer<String> onSelect) {
        this.onSelect = onSelect;
        setBackground(Color.WHITE);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                for (Map.Entry<Node, Rectangle> entry : hitboxes.entrySet()) {
                    if (entry.getValue().contains(e.getPoint())) {
                        selected = entry.getKey().sourceIp();
                        repaint();
                        onSelect.accept(selected);
                        return;
                    }
                }
            }
        });
    }

    public void setNodes(List<Node> newNodes) {
        nodes.clear();
        nodes.addAll(newNodes);
        revalidate();
        repaint();
    }

    public void setSelected(String sourceIp) {
        this.selected = sourceIp;
        repaint();
    }

    private int columns() {
        return Math.max(1, (getWidth() - MARGIN) / (W + GAP));
    }

    @Override
    public Dimension getPreferredSize() {
        int cols = columns();
        int rows = Math.max(1, (int) Math.ceil(nodes.size() / (double) cols));
        return new Dimension(getWidth(), MARGIN * 2 + rows * (H + GAP));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        hitboxes.clear();
        if (nodes.isEmpty()) {
            g2.setColor(new Color(0x9E, 0x9E, 0x9E));
            g2.drawString("No sources yet — nodes appear and colour as decoys are touched.", MARGIN, MARGIN + 20);
            return;
        }
        int cols = columns();
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            int x = MARGIN + (i % cols) * (W + GAP);
            int y = MARGIN + (i / cols) * (H + GAP);
            Rectangle box = new Rectangle(x, y, W, H);
            hitboxes.put(n, box);

            g2.setColor(fill(n.severity()));
            g2.fillRoundRect(x, y, W, H, 12, 12);
            boolean sel = n.sourceIp().equals(selected);
            g2.setColor(sel ? new Color(0x2D, 0x5F, 0xA8) : new Color(0x90, 0x90, 0x90));
            g2.setStroke(new java.awt.BasicStroke(sel ? 2.4f : 1.1f));
            g2.drawRoundRect(x, y, W, H, 12, 12);

            g2.setColor(new Color(0x21, 0x21, 0x21));
            g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
            drawCentered(g2, n.sourceIp(), x, y + 20, W);
            g2.setFont(getFont().deriveFont(11f));
            g2.setColor(new Color(0x42, 0x42, 0x42));
            String label = n.severity() == null ? "watching" : n.severity() + "  ·  " + n.alertCount() + " alerts";
            drawCentered(g2, label, x, y + 38, W);
        }
    }

    private static void drawCentered(Graphics2D g2, String text, int x, int baseline, int width) {
        FontMetrics fm = g2.getFontMetrics();
        int tx = x + (width - fm.stringWidth(text)) / 2;
        g2.drawString(text, tx, baseline);
    }

    private static Color fill(Severity severity) {
        if (severity == null) {
            return new Color(0xE0, 0xE0, 0xE0);
        }
        return switch (severity) {
            case LOW -> new Color(0xC8, 0xE6, 0xC9);
            case MEDIUM -> new Color(0xFF, 0xF5, 0x9D);
            case HIGH -> new Color(0xFF, 0xCC, 0x80);
            case CRITICAL -> new Color(0xEF, 0x9A, 0x9A);
        };
    }
}
