package com.mirage.console.ui;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

/**
 * Window sizes that fit the screen (FB-04): a window keeps its designed size on a big screen
 * and shrinks to the usable area (the screen minus task bars) on a small one, such as a
 * 1024x768 projector or a laptop at 125 % scaling.
 */
final class WindowSizing {

    private WindowSizing() {
    }

    /** {@code wanted}, shrunk to fit {@code usable}. */
    static Dimension fit(Dimension wanted, Rectangle usable) {
        return new Dimension(Math.min(wanted.width, usable.width), Math.min(wanted.height, usable.height));
    }

    /** {@code width x height}, shrunk to fit this screen's usable area. */
    static Dimension forScreen(int width, int height) {
        return fit(new Dimension(width, height),
                GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
    }
}
