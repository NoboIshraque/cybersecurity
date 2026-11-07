package com.mirage;

import com.mirage.common.BoundedLineReader;
import com.mirage.common.LineTooLongException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** B-08 / A-01: a line reader that refuses to buffer a line past its cap. */
class BoundedLineReaderTest {

    private static BoundedLineReader reader(String text, int max) {
        return new BoundedLineReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), max);
    }

    @Test
    void splitsOnLfCrlfAndLoneCrLikeBufferedReader() throws IOException {
        BoundedLineReader r = reader("one\ntwo\r\nthree\rfour\r\n\r\nlast", 100);
        assertEquals("one", r.readLine());
        assertEquals("two", r.readLine());
        assertEquals("three", r.readLine());
        assertEquals("four", r.readLine());
        assertEquals("", r.readLine(), "a blank line (end of HTTP headers) is kept");
        assertEquals("last", r.readLine(), "a final line without a newline is returned");
        assertNull(r.readLine());
    }

    @Test
    void decodesUtf8() throws IOException {
        assertEquals("pässwörd→", reader("pässwörd→\n", 100).readLine());
    }

    @Test
    void aLineAtTheCapIsFineOneByteMoreIsRefused() throws IOException {
        assertEquals("a".repeat(10), reader("a".repeat(10) + "\n", 10).readLine());
        LineTooLongException e = assertThrows(LineTooLongException.class,
                () -> reader("a".repeat(11) + "\n", 10).readLine());
        assertEquals(10, e.limit());
    }

    @Test
    void stopsReadingAtTheCapInsteadOfBufferingTheWholeStream() {
        // An endless line. An unbounded reader buffers until the heap runs out (it crashed the
        // test JVM with OutOfMemoryError), so this stream refuses to go past 1 MB instead.
        InputStream endless = new InputStream() {
            private int served;

            @Override
            public int read() throws IOException {
                if (++served > 1024 * 1024) {
                    throw new IOException("reader consumed over 1 MB of one line: it is unbounded");
                }
                return 'A';
            }
        };
        BoundedLineReader r = new BoundedLineReader(endless, 8192);
        assertThrows(LineTooLongException.class, r::readLine);
    }
}
