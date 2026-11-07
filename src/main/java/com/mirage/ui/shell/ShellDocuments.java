package com.mirage.ui.shell;

import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;

/**
 * Keeps a shell's on-screen text bounded (B-17). Threat popups live for the whole run, so
 * without a cap their text, and the cost of laying it out, would grow forever.
 */
public final class ShellDocuments {

    /** Most lines a shell pane keeps on screen. */
    public static final int MAX_LINES = 5000;

    private ShellDocuments() {
    }

    /** Remove the oldest lines so at most {@code maxLines} remain. Call on the EDT. */
    public static void trimHead(StyledDocument doc, int maxLines) {
        Element root = doc.getDefaultRootElement();
        // A document ending in "\n" has one more, empty, paragraph after the last line.
        int lines = root.getElementCount() - (endsWithNewline(doc) ? 1 : 0);
        if (lines <= maxLines) {
            return;
        }
        int cut = root.getElement(lines - maxLines).getStartOffset();
        try {
            doc.remove(0, cut);
        } catch (BadLocationException e) {
            // cut is inside the document by construction
        }
    }

    private static boolean endsWithNewline(StyledDocument doc) {
        try {
            return doc.getLength() > 0 && "\n".equals(doc.getText(doc.getLength() - 1, 1));
        } catch (BadLocationException e) {
            return false;
        }
    }
}
