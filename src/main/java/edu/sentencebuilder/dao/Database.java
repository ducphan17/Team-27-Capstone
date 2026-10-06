package edu.sentencebuilder.dao;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Opens JDBC connections using settings from {@code db.properties}.
 * The file is looked up in the working directory (project root) first, then on
 * the classpath. Nothing is hardcoded; copy {@code db.properties.example} to start.
 *
 * <p>Callers should use try-with-resources so each connection is closed.
 */
public final class Database {

    private static final String FILE_NAME = "db.properties";
    private static Properties properties;

    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        Properties p = settings();
        return DriverManager.getConnection(
                p.getProperty("db.url"),
                p.getProperty("db.user"),
                p.getProperty("db.password", ""));
    }

    private static synchronized Properties settings() throws SQLException {
        if (properties == null) {
            properties = load();
        }
        return properties;
    }

    private static Properties load() throws SQLException {
        Properties p = new Properties();
        Path local = Path.of(FILE_NAME);
        try {
            if (Files.isRegularFile(local)) {
                try (Reader in = Files.newBufferedReader(local)) {
                    p.load(in);
                }
            } else {
                try (InputStream in = Database.class.getClassLoader().getResourceAsStream(FILE_NAME)) {
                    if (in == null) {
                        throw new SQLException(FILE_NAME + " not found in " + local.toAbsolutePath().getParent()
                                + " or on the classpath. Copy db.properties.example to db.properties.");
                    }
                    p.load(in);
                }
            }
        } catch (IOException e) {
            throw new SQLException("Could not read " + FILE_NAME, e);
        }
        if (p.getProperty("db.url") == null || p.getProperty("db.user") == null) {
            throw new SQLException(FILE_NAME + " must define db.url and db.user");
        }
        return p;
    }
}
