package com.mirage;

import com.mirage.common.trace.TraceEvent;
import com.mirage.common.trace.TraceLevel;
import com.mirage.ui.shell.ShellDocuments;
import com.mirage.ui.shell.ShellModel;
import org.junit.jupiter.api.Test;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-17: a shell must stay close to real time under load and must not keep every line forever.
 * Delivered lines are let go, a big backlog drains fast, a backlog past the cap is summarised
 * (never dropping a banner), and the on-screen text is trimmed to its last lines.
 */
class ShellBacklogTest {

    private static TraceEvent line(TraceLevel level, String text) {
        return new TraceEvent(Instant.now(), "console", "engine", "stage", "10.10.0.5", level, text);
    }

    private static List<ShellModel.Line> drain(ShellModel model) {
        List<ShellModel.Line> all = new ArrayList<>();
        List<ShellModel.Line> batch;
        while (!(batch = model.poll(10_000)).isEmpty()) {
            all.addAll(batch);
        }
        return all;
    }

    @Test
    void deliveredLinesAreNotKept() {
        ShellModel model = new ShellModel();
        for (int i = 0; i < 1000; i++) {
            model.add(line(TraceLevel.INFO, "line " + i));
        }
        model.poll(400);
        assertEquals(600, model.held(), "only the undelivered lines are held");
        drain(model);
        assertEquals(0, model.held());
        assertEquals(1000, model.totalLines(), "the count still covers every line");
    }

    @Test
    void aBacklogPastTheCapIsSummarisedAndTheNewestLinesKept() {
        ShellModel model = new ShellModel();
        int extra = 500;
        for (int i = 0; i < ShellModel.MAX_BACKLOG + extra; i++) {
            model.add(line(TraceLevel.INFO, "line " + i));
        }
        assertTrue(model.held() <= ShellModel.MAX_BACKLOG, "held " + model.held());

        List<ShellModel.Line> shown = drain(model);
        assertTrue(shown.get(0).text().contains(extra + " lines skipped"), shown.get(0).text());
        assertEquals("line " + (ShellModel.MAX_BACKLOG + extra - 1), shown.get(shown.size() - 1).text());
        assertEquals("line " + extra, shown.get(1).text(), "the oldest lines go first");
    }

    @Test
    void bannersAreNeverSkipped() {
        ShellModel model = new ShellModel();
        model.add(line(TraceLevel.BANNER, "ACCESS GRANTED"));
        for (int i = 0; i < ShellModel.MAX_BACKLOG * 2; i++) {
            model.add(line(TraceLevel.INFO, "line " + i));
        }
        model.add(line(TraceLevel.BANNER, "CONNECTION BLOCKED"));
        List<ShellModel.Line> shown = drain(model);
        assertTrue(shown.stream().anyMatch(l -> l.banner() && l.text().equals("ACCESS GRANTED")));
        assertTrue(shown.stream().anyMatch(l -> l.banner() && l.text().equals("CONNECTION BLOCKED")));
    }

    @Test
    void aBigBacklogDrainsFastButASmallBurstStillTypesOut() {
        ShellModel model = new ShellModel();
        for (int i = 0; i < 1000; i++) {
            model.add(line(TraceLevel.INFO, "line " + i));
        }
        assertTrue(model.pollPaced(3).size() >= 1000 / ShellModel.CATCH_UP_TICKS,
                "a 1000-line backlog is caught up within about " + ShellModel.CATCH_UP_TICKS + " ticks");

        ShellModel small = new ShellModel();
        for (int i = 0; i < 10; i++) {
            small.add(line(TraceLevel.INFO, "line " + i));
        }
        assertEquals(3, small.pollPaced(3).size(), "a short burst keeps the slider's pace");
    }

    @Test
    void theOnScreenTextKeepsOnlyItsLastLines() throws BadLocationException {
        StyledDocument doc = new DefaultStyledDocument();
        for (int i = 0; i < 120; i++) {
            doc.insertString(doc.getLength(), "line " + i + "\n", null);
        }
        ShellDocuments.trimHead(doc, 100);
        String text = doc.getText(0, doc.getLength());
        assertTrue(text.startsWith("line 20\n"), text.substring(0, 20));
        assertTrue(text.endsWith("line 119\n"));
        assertEquals(100, text.split("\n").length);
    }
}
