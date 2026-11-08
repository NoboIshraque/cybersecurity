package com.mirage.console.ui;

import javax.swing.JSplitPane;
import java.awt.Component;

/**
 * A left/right split that keeps its divider at the same share of the width when the window
 * is resized, including before the window is first shown (FB-04: the dashboard's divider was
 * pinned at 560 px, so at 800 px the right side lost its buttons). When the operator drags
 * the divider, the new share is kept instead.
 */
final class ProportionalSplitPane extends JSplitPane {

    private double share;
    private int laidOutWidth = -1;
    private boolean adjusting;

    ProportionalSplitPane(Component left, Component right, double share) {
        super(HORIZONTAL_SPLIT, left, right);
        this.share = share;
        setResizeWeight(share);
        addPropertyChangeListener(DIVIDER_LOCATION_PROPERTY, e -> {
            int span = getWidth() - getDividerSize();
            if (!adjusting && span > 0 && getWidth() == laidOutWidth) { // moved by the operator
                this.share = Math.max(0.0, Math.min(1.0, getDividerLocation() / (double) span));
            }
        });
    }

    @Override
    public void doLayout() {
        adjusting = true;
        try {
            int span = getWidth() - getDividerSize();
            if (getWidth() != laidOutWidth && span > 0) {
                laidOutWidth = getWidth();
                setDividerLocation((int) Math.round(span * share));
            }
            super.doLayout();
        } finally {
            adjusting = false;
        }
    }
}
