package edu.sentencebuilder.parser;

import edu.sentencebuilder.dao.FileDAO;
import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.model.SourceFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL tests: run with mvn -Pdb-tests verify, never against the app database. */
class FileImporterIT {
    private static final Properties SETTINGS = new Properties();
    private static final String CORPUS = "Zorvex miplex. Zorvex talvex.";

    @BeforeAll
    static void initialize() throws Exception {
        Path config = Path.of("db-test.properties");
        assertTrue(Files.isRegularFile(config),
                "Copy db-test.properties.example to db-test.properties and configure the isolated database");
        try (Reader reader = Files.newBufferedReader(config)) {
            SETTINGS.load(reader);
        }
        assertEquals("sentencebuilder_test", SETTINGS.getProperty("db.user"),
                "Use the dedicated test user, which has privileges only on the test database");
        // No fallback to Database.getConnection(): missing test settings must fail the build.
        try (Connection conn = open();
             var resource = FileImporterIT.class.getResourceAsStream("/schema-test.sql")) {
            assertNotNull(resource);
            String schema = new String(resource.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("(?m)--.*$", "");
            // This fixture contains only table DDL; database/user creation is an explicit setup step.
            for (String sql : schema.split(";")) {
                if (!sql.isBlank()) {
                    try (var statement = conn.createStatement()) {
                        statement.execute(sql);
                    }
                }
            }
        }
    }

    private static Connection open() throws SQLException {
        Connection conn = DriverManager.getConnection(SETTINGS.getProperty("db.url"),
                SETTINGS.getProperty("db.user"), SETTINGS.getProperty("db.password", ""));
        try {
            // Check the server's selected database, rather than trusting a URL substring.
            if (!"sentencebuilder_test".equals(conn.getCatalog())) {
                throw new SQLException("Refusing test writes outside sentencebuilder_test");
            }
            return conn;
        } catch (SQLException | RuntimeException error) {
            conn.close();
            throw error;
        }
    }

    @BeforeEach
    @AfterEach
    void clearTestTables() throws SQLException {
        // Every cleanup connection passes the guard above. Delete children before parents.
        try (Connection conn = open(); var statement = conn.createStatement()) {
            statement.executeUpdate("DELETE FROM generated_sentence");
            statement.executeUpdate("DELETE FROM word_pair");
            statement.executeUpdate("DELETE FROM source_file");
            statement.executeUpdate("DELETE FROM word");
        }
    }

    private static FileImporter importer(FileDAO files) {
        WordDAO words = new WordDAO();
        return new FileImporter(Path.of("repeat-check.txt"), new Tokenizer(), words,
                new WordPairDAO(words), files, FileImporterIT::open);
    }

    private static void importText(String text, FileDAO files) throws SQLException {
        List<List<String>> sentences = new Tokenizer().tokenize(text);
        FileImporter.Stats stats = new FileImporter.Stats();
        sentences.forEach(stats::addSentence);
        // Same validation + transaction entry point used by FileImporter.call().
        importer(files).writeToDatabase(stats, sentences.size());
    }

    private static List<List<Object>> rows(Connection conn, String sql) throws SQLException {
        List<List<Object>> result = new ArrayList<>();
        try (var statement = conn.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    row.add(rs.getObject(i));
                }
                result.add(row);
            }
        }
        return result;
    }

    private static Map<String, List<List<Object>>> snapshot() throws SQLException {
        // Fresh connections observe committed data; include IDs, timestamps, and all four tables.
        Map<String, List<List<Object>>> result = new LinkedHashMap<>();
        try (Connection conn = open()) {
            for (String query : List.of("SELECT * FROM word ORDER BY word_id",
                    "SELECT * FROM word_pair ORDER BY from_word_id, to_word_id",
                    "SELECT * FROM source_file ORDER BY file_id",
                    "SELECT * FROM generated_sentence ORDER BY id")) {
                result.put(query, rows(conn, query));
            }
        }
        return result;
    }

    private static void seed() throws SQLException {
        importText(CORPUS, new FileDAO());
        try (Connection conn = open(); var statement = conn.createStatement()) {
            statement.executeUpdate("""
                    UPDATE word_pair p
                    JOIN word f ON p.from_word_id = f.word_id
                    JOIN word t ON p.to_word_id = t.word_id
                    SET p.user_count = 7 WHERE f.text = 'zorvex' AND t.text = 'miplex'
                    """);
            // Include unrelated app data in snapshots so failed imports must preserve it too.
            statement.executeUpdate("""
                    INSERT INTO generated_sentence (sentence, start_word, algorithm)
                    VALUES ('Zorvex miplex.', 'zorvex', 'Most frequent')
                    """);
        }
    }

    @Test
    void repeatedImportsKeepIdsAndUserCounts() throws SQLException {
        seed();
        List<List<Object>> originalIds;
        try (Connection conn = open()) {
            originalIds = rows(conn, "SELECT text, word_id, first_seen FROM word ORDER BY text");
        }
        importText(CORPUS, new FileDAO());
        try (Connection conn = open()) {
            assertEquals(originalIds, rows(conn,
                    "SELECT text, word_id, first_seen FROM word ORDER BY text"));
            assertEquals(List.of(List.of("miplex", 2, 0, 2), List.of("talvex", 2, 0, 2),
                    List.of("zorvex", 4, 4, 0)), rows(conn,
                    "SELECT text, total_count, start_count, end_count FROM word ORDER BY text"));
            assertEquals(List.of(List.of("zorvex", "miplex", 2, 7), List.of("zorvex", "talvex", 2, 0)),
                    rows(conn, """
                    SELECT f.text, t.text, p.count, p.user_count FROM word_pair p
                    JOIN word f ON p.from_word_id = f.word_id
                    JOIN word t ON p.to_word_id = t.word_id ORDER BY f.text, t.text
                    """));
            assertEquals(List.of(List.of("repeat-check.txt", 4, 2), List.of("repeat-check.txt", 4, 2)),
                    rows(conn, "SELECT filename, word_count, sentence_count FROM source_file ORDER BY file_id"));
        }
    }

    private static void verifyRollback(boolean runtimeFailure) throws SQLException {
        seed();
        var before = snapshot();
        FileDAO failingFiles = new FileDAO() {
            @Override
            public SourceFile insert(Connection conn, SourceFile file) throws SQLException {
                super.insert(conn, file);
                // Fail after ALL writes, proving words, pairs, and file metadata roll back together.
                if (runtimeFailure) {
                    throw new IllegalStateException("Intentional runtime rollback test");
                }
                throw new SQLException("Intentional SQL rollback test");
            }
        };
        String text = "Zorvex miplex. Rollbackzor rollbackmip.";
        if (runtimeFailure) {
            assertThrows(IllegalStateException.class, () -> importText(text, failingFiles));
        } else {
            assertThrows(SQLException.class, () -> importText(text, failingFiles));
        }
        assertEquals(before, snapshot());
    }

    @Test
    void sqlFailureRollsBackAllWrites() throws SQLException {
        verifyRollback(false);
    }

    @Test
    void runtimeFailureRollsBackAllWrites() throws SQLException {
        verifyRollback(true);
    }

    @Test
    void acceptsOneHundredCharacterWordInMysql() throws SQLException {
        String word = "a".repeat(100);
        importText(word + ".", new FileDAO());
        try (Connection conn = open()) {
            assertEquals(List.of(List.of(word, 1, 1, 1)), rows(conn,
                    "SELECT text, total_count, start_count, end_count FROM word"));
            assertEquals(List.of(List.of(1, 1)), rows(conn,
                    "SELECT word_count, sentence_count FROM source_file"));
            assertTrue(rows(conn, "SELECT * FROM word_pair").isEmpty());
        }
    }

    @Test
    void oversizedWordLeavesEveryStoredRowUnchanged() throws SQLException {
        seed();
        var before = snapshot();
        var error = assertThrows(IllegalArgumentException.class, () -> importText(
                "Zorvex miplex. Freshword freshpair. " + "a".repeat(101) + ".", new FileDAO()));
        assertTrue(error.getMessage().contains("100-character limit (101 characters)"));
        assertTrue(error.getMessage().contains("No data was imported."));
        assertEquals(before, snapshot());
    }
}
