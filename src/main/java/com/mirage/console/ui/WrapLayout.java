package com.mirage.console.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A {@link FlowLayout} that reports the size of its rows once they wrap (FB-04). A plain
 * FlowLayout already wraps when it lays out, but it asks for one long row, so the container
 * above it gives it one row's height and the wrapped buttons fall outside the window. This
 * one asks for the height its rows need at its current width, and its minimum width is that
 * of its widest component, so a split pane can still make it narrower.
 *
 * <p>The width a container gets is only known when its parent lays it out, after the parent
 * asked for its preferred height. So when the rows at the real width need a different height
 * than was reported, the window is laid out once more ({@link #relayOutWindow}).
 */
final class WrapLayout extends FlowLayout {

    /** Most extra layout passes for one change; rows and notes settle in one or two. */
    private static final int MAX_PASSES = 4;
    /** EDT-confined: true while extra passes run; a request made during one is queued for the next. */
    private static boolean relayingOut;
    private static final Set<Component> pending = new LinkedHashSet<>();

    /**
     * The heights handed out since the last layout. A parent may ask while this container still
     * has its old width and keep that answer, then ask again after giving it the new width; if
     * any answer differs from what the rows need at the real width, a parent used a wrong one.
     */
    private int lowestReported = Integer.MAX_VALUE;
    private int highestReported = Integer.MIN_VALUE;
    /** The height the rows needed at the last layout. */
    private int laidOutHeight = -1;

    WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        Dimension size = rowsSize(target);
        lowestReported = Math.min(lowestReported, size.height);
        highestReported = Math.max(highestReported, size.height);
        return size;
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            Insets in = target.getInsets();
            int width = 0;
            int height = 0;
            for (Component c : target.getComponents()) {
                if (c.isVisible()) {
                    Dimension d = c.getMinimumSize();
                    width = Math.max(width, d.width);
                    height = Math.max(height, d.height);
                }
            }
            return new Dimension(width + in.left + in.right + getHgap() * 2,
                    height + in.top + in.bottom + getVgap() * 2);
        }
    }

    @Override
    public void layoutContainer(Container target) {
        super.layoutContainer(target); // FlowLayout already wraps the rows at the real width
        if (target.getWidth() <= 0) {
            return;
        }
        int needed = rowsSize(target).height;
        boolean asked = lowestReported != Integer.MAX_VALUE;
        boolean stale = asked ? lowestReported != needed || highestReported != needed
                : needed != laidOutHeight; // not asked: a parent may still hold the old answer
        lowestReported = Integer.MAX_VALUE;
        highestReported = Integer.MIN_VALUE;
        laidOutHeight = needed;
        if (stale) {
            relayOutWindow(target);
        }
    }

    /**
     * Lay {@code c}'s window out again now that {@code c} knows its width. A request made while
     * such a pass runs is done in the next pass, up to {@link #MAX_PASSES}, so this always ends.
     */
    static void relayOutWindow(Component c) {
        pending.add(c);
        if (relayingOut) {
            return;
        }
        relayingOut = true;
        try {
            for (int pass = 0; pass < MAX_PASSES && !pending.isEmpty(); pass++) {
                Set<Container> tops = new LinkedHashSet<>();
                for (Component p : pending) {
                    p.invalidate();
                    tops.add(topOf(p));
                }
                pending.clear();
                tops.forEach(Container::validate); // does nothing without a native window (headless)
            }
        } finally {
            pending.clear();
            relayingOut = false;
        }
    }

    private static Container topOf(Component c) {
        Container top = c instanceof Container k ? k : c.getParent();
        while (!(top instanceof Window) && top.getParent() != null) {
            top = top.getParent();
        }
        return top;
    }

    /** The size of the rows at the target's width (or, before it has one, its nearest sized ancestor's). */
    private Dimension rowsSize(Container target) {
        synchronized (target.getTreeLock()) {
            Container sized = target;
            while (sized.getWidth() == 0 && sized.getParent() != null) {
                sized = sized.getParent();
            }
            int targetWidth = sized.getWidth() == 0 ? Integer.MAX_VALUE : sized.getWidth();
            Insets in = target.getInsets();
            int hgap = getHgap();
            int vgap = getVgap();
            int horizontal = in.left + in.right + hgap * 2;
            int maxRow = targetWidth - horizontal;

            int width = 0;
            int height = 0;
            int rowWidth = 0;
            int rowHeight = 0;
            int rows = 0;
            for (Component c : target.getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                Dimension d = c.getPreferredSize();
                if (rowWidth > 0 && rowWidth + d.width > maxRow) { // FlowLayout's own wrapping rule
                    width = Math.max(width, rowWidth);
                    height += rowHeight + (rows > 0 ? vgap : 0);
                    rows++;
                    rowWidth = 0;
                    rowHeight = 0;
                }
                rowWidth += (rowWidth > 0 ? hgap : 0) + d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            if (rowWidth > 0) {
                width = Math.max(width, rowWidth);
                height += rowHeight + (rows > 0 ? vgap : 0);
            }
            return new Dimension(width + horizontal, height + in.top + in.bottom + vgap * 2);
        }
    }
}
