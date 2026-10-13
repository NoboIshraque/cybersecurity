package com.mirage.console.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Opens an embedded H2 database and applies the schema on startup. */
public final class Database implements AutoCloseable {

    private final Connection connection;

    public Database(String jdbcUrl) throws SQLException {
        this.connection = DriverManager.getConnection(jdbcUrl);
        runSchema();
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
