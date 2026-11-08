package com.mirage.console.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/** Opens an embedded H2 database and applies the schema on startup. */
public final class Database implements AutoCloseable {

    private final Connection connection;

    public Database(String jdbcUrl) throws SQLException {
        this(DriverManager.getConnection(jdbcUrl));
    }

    private Database(Connection connection) throws SQLException {
        this.connection = connection;
        runSchema();
    }

    /** Environment variable that, when set, encrypts the database file (A-07). */
    public static final String PASSWORD_ENV = "MIRAGE_DB_PASSWORD";

    /** H2 error codes for "this key/user does not open this file" (wrong password, or plain vs encrypted). */
    private static final Set<Integer> WRONG_KEY = Set.of(28000, 90030, 90048, 90049);
    /** H2's "database may be already in use" (the file is locked by another process). */
    private static final int ALREADY_OPEN = 90020;
    /**
     * FA-01: how long a locked file is retried before it counts as in use. H2's lock is an OS lock
     * on {@code mirage.mv.db}; after a force-kill Windows drops it only once the dead JVM is fully
     * torn down, which can be a moment after the operator starts again. A live Console keeps its
     * lock, so a real second instance is still refused, just this much later.
     */
    private static final long STALE_LOCK_WAIT_MS = 10_000;
    private static final long STALE_LOCK_RETRY_MS = 250;

    /**
     * Open (or create) the Console database {@code dir/mirage} (A-07). With a password the file
     * is AES-encrypted on disk and the database user needs the same password, so copying or
     * opening the file without it gives nothing. Without one it is a plain file, as before.
     *
     * @throws IllegalArgumentException when the password doesn't match how the file was created,
     *         with a one-line explanation for the operator
     * @throws IllegalStateException when another process still has the database open after
     *         {@value #STALE_LOCK_WAIT_MS} ms (B-12, FA-01)
     */
    public static Database openFile(Path dir, String password) throws SQLException {
        boolean locked = password != null && !password.isEmpty();
        Path file = dir.resolve("mirage");
        String url = "jdbc:h2:file:" + file.toAbsolutePath() + (locked ? ";CIPHER=AES" : "");
        long deadline = System.nanoTime() + STALE_LOCK_WAIT_MS * 1_000_000;
        boolean told = false;
        while (true) {
            try {
                Connection c = locked
                        ? DriverManager.getConnection(url, "mirage", password + " " + password) // file key + user password
                        : DriverManager.getConnection(url);
                return new Database(c);
            } catch (SQLException e) {
                if (e.getErrorCode() != ALREADY_OPEN) {
                    throw explain(e, file, locked);
                }
                if (System.nanoTime() - deadline >= 0) {
                    throw inUse(file, e);
                }
                if (!told) {
                    System.out.println("[wait] " + file + " is locked: waiting up to " + STALE_LOCK_WAIT_MS / 1000
                            + " s for a stale database lock from a previous run to clear...");
                    told = true;
                }
                try {
                    Thread.sleep(STALE_LOCK_RETRY_MS);
                } catch (InterruptedException stop) {
                    Thread.currentThread().interrupt();
                    throw inUse(file, e);
                }
            }
        }
    }

    /** B-12: a second Console (or defender) started in the same folder, and the lock didn't clear. */
    private static IllegalStateException inUse(Path file, SQLException cause) {
        return new IllegalStateException(file + " is in use: another Mirage Console (or defender) is "
                + "already running in this folder. Close it, or start this one from another folder.", cause);
    }

    /** A wrong key becomes a one-line {@link IllegalArgumentException}; any other failure is returned as is. */
    private static SQLException explain(SQLException e, Path file, boolean locked) {
        if (!WRONG_KEY.contains(e.getErrorCode())) {
            return e;
        }
        throw new IllegalArgumentException(locked
                ? "the database " + file + " can't be opened with this " + PASSWORD_ENV
                        + " (wrong password, or the file was created without one)"
                : "the database " + file + " is encrypted: set " + PASSWORD_ENV + " to the password it was "
                        + "created with", e);
    }

    private void runSchema() throws SQLException {
        String sql;
        try (InputStream in = Database.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new SQLException("schema.sql not found on the classpath");
            }
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("failed to read schema.sql", e);
        }

        // Drop comment and blank lines first, so a ';' inside a comment can't split a statement.
        StringBuilder cleaned = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                cleaned.append(line).append('\n');
            }
        }

        try (Statement st = connection.createStatement()) {
            for (String statement : cleaned.toString().split(";")) {
                String ddl = statement.trim();
                if (!ddl.isEmpty()) {
                    st.execute(ddl);
                }
            }
        }
    }

    Connection connection() {
        return connection;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
