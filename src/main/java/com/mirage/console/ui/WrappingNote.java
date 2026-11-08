package com.mirage.console.ui;

import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import java.awt.Dimension;

/**
 * A line of explanatory text that looks like a label but wraps onto more lines when the
 * window is narrow, instead of being cut off (FB-04). Its height follows its width, so when
 * it is laid out at a new width it asks for one more layout pass if it needs a different height.
 */
final class WrappingNote extends JTextArea {

    WrappingNote(String text) {
        super(text);
        setLineWrap(true);
        setWrapStyleWord(true);
        setEditable(false);
        setFocusable(false);
        setHighlighter(null);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder());
        setFont(UIManager.getFont("Label.font"));
        setForeground(UIManager.getColor("Label.foreground"));
        setAlignmentX(LEFT_ALIGNMENT);
    }

    /** As narrow as a few words: the text wraps, so it never holds a split pane open. */
    @Override
    public Dimension getMinimumSize() {
        return new Dimension(60, super.getMinimumSize().height);
    }

    @Override
    public void doLayout() {
        super.doLayout();
        if (getWidth() > 0 && getPreferredSize().height != getHeight()) {
            WrapLayout.relayOutWindow(this); // rewrapped at its new width: give it the height it needs
        }
    }
}
