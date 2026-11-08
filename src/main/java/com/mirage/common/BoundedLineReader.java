package com.mirage.common;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads UTF-8 lines like {@link java.io.BufferedReader#readLine()} (ends at LF, CRLF or a
 * lone CR; a last line without a newline is returned), but never holds more than
 * {@code maxBytes} of one line: past that it throws {@link LineTooLongException}, so a peer
 * that never sends a newline can't make us buffer without limit (B-08, A-01).
 *
 * <p>Not thread-safe: one reader per connection, read by one thread.
 */
public final class BoundedLineReader {

    private final InputStream in;
    private final int maxBytes;
    private final ByteArrayOutputStream line;

    public BoundedLineReader(InputStream in, int maxBytes) {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes must be at least 1");
        }
        this.in = in.markSupported() ? in : new BufferedInputStream(in);
        this.maxBytes = maxBytes;
        this.line = new ByteArrayOutputStream(Math.min(maxBytes, 256));
    }

    /** The next line without its terminator, or {@code null} at end of stream. */
    public String readLine() throws IOException {
        line.reset();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                return text();
            }
            if (b == '\r') {
                in.mark(1);
                if (in.read() != '\n') {
                    in.reset(); // a lone CR ends the line too; keep what follows
                }
                return text();
            }
            if (line.size() >= maxBytes) {
                throw new LineTooLongException(maxBytes);
            }
            line.write(b);
        }
        return line.size() == 0 ? null : text();
    }

    private String text() {
        return line.toString(StandardCharsets.UTF_8);
    }
}
