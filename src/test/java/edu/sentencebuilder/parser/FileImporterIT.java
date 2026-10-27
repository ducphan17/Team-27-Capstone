package edu.sentencebuilder.parser;

import edu.sentencebuilder.dao.FileDAO;
import edu.sentencebuilder.dao.SentenceDAO;
import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.model.SourceFile;
import edu.sentencebuilder.model.GeneratedSentence;
import edu.sentencebuilder.model.Word;
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
            // Rebuild the disposable schema on each suite run so changed column definitions
            // take effect. The connection has already passed the database-name guard.
            try (var statement = conn.createStatement()) {
                statement.executeUpdate("DROP TABLE IF EXISTS word_pair");
                statement.executeUpdate("DROP TABLE IF EXISTS generated_sentence");
                statement.executeUpdate("DROP TABLE IF EXISTS source_file");
                statement.executeUpdate("DROP TABLE IF EXISTS word");
            }
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
        TextImporter.Stats stats = new TextImporter.Stats();
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
        var error = assertThrows(InvalidImportException.class, () -> importText(
                "Zorvex miplex. Freshword freshpair. " + "a".repeat(101) + ".", new FileDAO()));
        assertTrue(error.getMessage().contains("100-character limit (101 characters)"));
        assertTrue(error.getMessage().contains("No data was imported."));
        assertEquals(before, snapshot());
    }

    @Test
    void startingWordQueryExcludesWordsWithZeroStartCount() throws SQLException {
        importText(CORPUS, new FileDAO());

        try (Connection conn = open()) {
            // The fixture contains two zero-start words and one starter, used twice.
            List<Word> starters = new WordDAO().findStartingWords(conn);
            assertEquals(List.of("zorvex"), starters.stream().map(Word::getText).toList());
            assertEquals(2, starters.getFirst().getStartCount());
            assertTrue(starters.getFirst().getWordId() > 0);
            assertFalse(conn.isClosed(), "The caller still owns the connection");
        }
    }

    @Test
    void generatedSentencesSaveTextStartWordAndAlgorithm() throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (Connection conn = open()) {
            for (String algorithm : List.of("Weighted random", "Most frequent")) {
                GeneratedSentence sentence = new GeneratedSentence(
                        "Zorvex miplex.", "zorvex", algorithm);
                assertSame(sentence, new SentenceDAO().insert(conn, sentence));
                assertTrue(sentence.getId() > 0, "The insert must populate its generated ID");
                ids.add(sentence.getId());
            }
            assertNotEquals(ids.getFirst(), ids.getLast());
            assertFalse(conn.isClosed());
        }

        // A fresh connection checks durable rows, rather than only the in-memory models.
        try (Connection conn = open()) {
            assertEquals(List.of(
                    List.of("Zorvex miplex.", "zorvex", "Weighted random"),
                    List.of("Zorvex miplex.", "zorvex", "Most frequent")), rows(conn,
                    "SELECT sentence, start_word, algorithm FROM generated_sentence ORDER BY id"));
            assertTrue(rows(conn,
                    "SELECT id FROM generated_sentence WHERE added_to_corpus = TRUE").isEmpty());
            assertTrue(rows(conn,
                    "SELECT id FROM generated_sentence WHERE created_at IS NULL").isEmpty());
        }
    }

    /** Exact corpus assertions shared by the text-import and commit tests. */
    private static void assertTextCorpus(Connection conn) throws SQLException {
        assertEquals(List.of(List.of("miplex", 1, 0, 1), List.of("talvex", 1, 0, 1),
                List.of("zorvex", 2, 2, 0)), rows(conn,
                "SELECT text, total_count, start_count, end_count FROM word ORDER BY text"));
        assertEquals(List.of(List.of("zorvex", "miplex", 1, 0),
                List.of("zorvex", "talvex", 1, 0)), rows(conn, """
                SELECT f.text, t.text, p.count, p.user_count FROM word_pair p
                JOIN word f ON p.from_word_id = f.word_id
                JOIN word t ON p.to_word_id = t.word_id ORDER BY f.text, t.text
                """));
        assertEquals(List.of(List.of("batch-generated", 4, 2)), rows(conn,
                "SELECT filename, word_count, sentence_count FROM source_file ORDER BY file_id"));
    }

    @Test
    void textImportSavesCountsPairsAndSourceRecord() throws SQLException {
        try (Connection conn = open()) {
            conn.setAutoCommit(false);
            try {
                // Exercise the public text entry point; no temporary file is read.
                new TextImporter().importText(conn, CORPUS, "batch-generated");
                assertFalse(conn.isClosed());
                assertFalse(conn.getAutoCommit());
                assertTextCorpus(conn);
                conn.commit();
            } finally {
                conn.rollback();
            }
        }

        try (Connection conn = open()) {
            assertTextCorpus(conn);
            assertTrue(rows(conn, "SELECT * FROM generated_sentence").isEmpty());
        }
    }

    private static List<Integer> saveBatchSentences() throws SQLException {
        try (Connection conn = open()) {
            SentenceDAO sentences = new SentenceDAO();
            GeneratedSentence first = sentences.insert(conn,
                    new GeneratedSentence("Zorvex miplex.", "zorvex", "Weighted random"));
            GeneratedSentence second = sentences.insert(conn,
                    new GeneratedSentence("Zorvex talvex.", "zorvex", "Most frequent"));
            // This unselected result must never be marked by the batch operation.
            sentences.insert(conn,
                    new GeneratedSentence("Unselected result.", "unselected", "Most frequent"));
            return List.of(first.getId(), second.getId());
        }
    }

    @Test
    void textCountsAndSelectedSentenceFlagsCommitTogether() throws SQLException {
        List<Integer> ids = saveBatchSentences();
        var before = snapshot();

        SentenceDAO observingSentences = new SentenceDAO() {
            @Override
            public void markAddedToCorpus(Connection conn, List<Integer> selectedIds)
                    throws SQLException {
                super.markAddedToCorpus(conn, selectedIds);
                assertTextCorpus(conn);
                assertEquals(List.of(List.of("Zorvex miplex."), List.of("Zorvex talvex.")), rows(conn,
                        "SELECT sentence FROM generated_sentence WHERE added_to_corpus = TRUE ORDER BY id"));
                // The real service must keep both sets of writes private until its commit.
                assertEquals(before, snapshot());
            }
        };
        new CorpusService(FileImporterIT::open, new TextImporter(), observingSentences)
                .addToCorpus(CORPUS, ids);

        try (Connection conn = open()) {
            assertTextCorpus(conn);
            assertEquals(List.of(List.of("Zorvex miplex."), List.of("Zorvex talvex.")), rows(conn,
                    "SELECT sentence FROM generated_sentence WHERE added_to_corpus = TRUE ORDER BY id"));
        }
    }

    private static void verifyTextAndFlagRollback(boolean failAfterMarking) throws SQLException {
        seed();
        List<Integer> ids = saveBatchSentences();
        var before = snapshot();

        SentenceDAO failingSentences = new SentenceDAO() {
            @Override
            public void markAddedToCorpus(Connection conn, List<Integer> selectedIds)
                    throws SQLException {
                // Reaching this DAO proves the service has already imported the text.
                assertEquals(List.of(List.of("freshpair"), List.of("freshword")), rows(conn,
                        "SELECT text FROM word WHERE text LIKE 'fresh%' ORDER BY text"));
                assertEquals(before, snapshot(), "Import must still be uncommitted");
                if (failAfterMarking) {
                    super.markAddedToCorpus(conn, selectedIds);
                    assertEquals(List.of(List.of("Zorvex miplex."), List.of("Zorvex talvex.")), rows(conn,
                            "SELECT sentence FROM generated_sentence WHERE added_to_corpus = TRUE ORDER BY id"));
                    throw new IllegalStateException("Intentional batch failure after marking");
                }
                throw new SQLException("Intentional batch failure before marking");
            }
        };
        CorpusService service = new CorpusService(
                FileImporterIT::open, new TextImporter(), failingSentences);
        String text = "Zorvex miplex. Freshword freshpair.";
        if (failAfterMarking) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> service.addToCorpus(text, ids));
            assertEquals("Intentional batch failure after marking", error.getMessage());
        } else {
            SQLException error = assertThrows(SQLException.class,
                    () -> service.addToCorpus(text, ids));
            assertEquals("Intentional batch failure before marking", error.getMessage());
        }

        // Includes existing counts, user counts, IDs, timestamps, source rows, and flags.
        assertEquals(before, snapshot());
    }

    @Test
    void failureAfterTextImportRollsBackCountsAndLeavesFlagsUnchanged() throws SQLException {
        verifyTextAndFlagRollback(false);
    }

    @Test
    void failureAfterMarkingRollsBackBothCountsAndFlags() throws SQLException {
        verifyTextAndFlagRollback(true);
    }

    private static List<List<Object>> userPairs(Connection conn) throws SQLException {
        return rows(conn, """
                SELECT f.text, t.text, p.count, p.user_count
                FROM word_pair p
                JOIN word f ON f.word_id = p.from_word_id
                JOIN word t ON t.word_id = p.to_word_id
                ORDER BY f.text, t.text
                """);
    }

    // Tien Phan
    @Test
    void autocompleteAddsUnknownWordsAndCountsOnlyUserChoices() throws SQLException {
        seed();
        List<List<Object>> importedWords;
        try (Connection conn = open()) {
            importedWords = rows(conn, "SELECT * FROM word ORDER BY word_id");
        }

        AutocompleteService service = new AutocompleteService(FileImporterIT::open);
        // Known pair zorvex -> miplex (user_count 7 -> 8), then an unknown word and new pair.
        // The one-word second sentence adds a word but no pair across the sentence end.
        service.recordSentences(List.of(List.of("zorvex", "miplex", "glorp"), List.of("blim")));
        service.recordSentences(List.of(List.of("miplex", "glorp"))); // same pair again: no duplicate word

        try (Connection conn = open()) {
            // Imported rows keep their IDs, counts, and first_seen; new words have zero counts.
            List<List<Object>> words = rows(conn, "SELECT * FROM word ORDER BY word_id");
            assertEquals(importedWords, words.subList(0, importedWords.size()));
            assertEquals(List.of(List.of("blim", 0, 0, 0), List.of("glorp", 0, 0, 0)), rows(conn, """
                    SELECT text, total_count, start_count, end_count FROM word
                    WHERE text IN ('glorp', 'blim') ORDER BY text
                    """));
            // Autocomplete changes user_count only; imported pair counts stay the same.
            assertEquals(List.of(
                    List.of("miplex", "glorp", 0, 2),
                    List.of("zorvex", "miplex", 1, 8),
                    List.of("zorvex", "talvex", 1, 0)), userPairs(conn));
        }
    }

    @Test
    void autocompleteFailureRollsBackTheNewWord() throws SQLException {
        seed();
        var before = snapshot();

        WordDAO words = new WordDAO();
        WordPairDAO failingPairs = new WordPairDAO(words) {
            @Override
            public void incrementUserCount(Connection conn, int fromWordId, int toWordId)
                    throws SQLException {
                // Reaching this DAO proves the service already inserted the new word.
                assertEquals(List.of(List.of("glorp")),
                        rows(conn, "SELECT text FROM word WHERE text = 'glorp'"));
                assertEquals(before, snapshot(), "The new word must still be uncommitted");
                throw new SQLException("Intentional autocomplete failure");
            }
        };
        AutocompleteService service = new AutocompleteService(FileImporterIT::open, words, failingPairs);

        SQLException error = assertThrows(SQLException.class,
                () -> service.recordSentences(List.of(List.of("zorvex", "glorp"))));
        assertEquals("Intentional autocomplete failure", error.getMessage());
        assertEquals(before, snapshot());
    }
}
