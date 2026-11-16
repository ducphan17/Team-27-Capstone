package edu.sentencebuilder.ui;

import edu.sentencebuilder.dao.SentenceDAO;
import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.model.GeneratedSentence;
import edu.sentencebuilder.model.Word;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production batch's failure and progress rules without MySQL or JavaFX startup. */
class BatchGenerateOutcomesTest {
    private final List<String> calls = new ArrayList<>();
    private final List<Integer> progress = new ArrayList<>();

    private Connection connection(boolean closeFails) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    if (method.getName().equals("close") && closeFails) {
                        throw new SQLException("close failed");
                    }
                    if (method.getName().equals("setAutoCommit")) {
                        assertEquals(true, args[0]);
                    }
                    return null;
                });
    }

    private WordDAO words(Connection expected, boolean empty) {
        Word word = new Word(1, "hello", 1, 1, 1, null);
        return new WordDAO() {
            @Override
            public List<Word> findStartingWords(Connection conn) {
                assertSame(expected, conn);
                return empty ? List.of() : List.of(word);
            }

            @Override
            public Optional<Word> findByText(Connection conn, String text) {
                assertSame(expected, conn);
                return Optional.of(word);
            }
        };
    }

    private SentenceDAO sentences(Connection expected, int failAt) {
        return new SentenceDAO() {
            private int attempts;

            @Override
            public GeneratedSentence insert(Connection conn, GeneratedSentence sentence)
                    throws SQLException {
                assertSame(expected, conn);
                if (++attempts == failAt) {
                    throw new SQLException("insert failed");
                }
                sentence.setId(attempts);
                return sentence;
            }
        };
    }

    private BatchGenerateController.BatchResult run(Connection conn, boolean empty, int failAt)
            throws Exception {
        return BatchGenerateController.generateBatch(3, "Most frequent", (saved, total) -> {
            assertEquals(3, total);
            progress.add(saved);
        }, () -> conn, words(conn, empty), sentences(conn, failAt));
    }

    @Test
    void whitespaceStartingWordKeepsAutomaticSelection() throws Exception {
        Connection conn = connection(false);
        var result = BatchGenerateController.generateBatch(3, "Most frequent", " \t ",
                (saved, total) -> progress.add(saved), () -> conn,
                words(conn, false), sentences(conn, 0));
        assertNull(result.failure());
        assertTrue(result.sentences().stream().allMatch(s -> s.getStartWord().equals("hello")));
        assertEquals(List.of(1, 2, 3), progress);
    }

    @Test
    void chosenWordIsNormalizedAndUsedForEverySentenceWithOneConnection() throws Exception {
        for (String algorithm : List.of("Most frequent", "Weighted random")) {
            calls.clear();
            Connection conn = connection(false);
            int[] opened = {0};
            WordDAO chosenWords = new WordDAO() {
                @Override
                public List<Word> findStartingWords(Connection connection) {
                    fail("Explicit words must bypass automatic starting-word loading");
                    return List.of();
                }

                @Override
                public Optional<Word> findByText(Connection connection, String text) {
                    assertSame(conn, connection);
                    assertEquals("chosen", text);
                    // A word need not have started an imported sentence to be explicitly chosen.
                    return Optional.of(new Word(2, "chosen", 1, 0, 1, null));
                }
            };
            var result = BatchGenerateController.generateBatch(3, algorithm, "  CHOSEN  ",
                    (saved, total) -> {}, () -> { opened[0]++; return conn; },
                    chosenWords, sentences(conn, 0));
            assertEquals(1, opened[0]);
            assertNull(result.failure());
            assertEquals(3, result.sentences().size());
            for (var sentence : result.sentences()) {
                assertEquals("chosen", sentence.getStartWord());
                assertEquals("Chosen.", sentence.getSentence());
                assertEquals(algorithm, sentence.getAlgorithm());
            }
            assertEquals(List.of("setAutoCommit", "close"), calls);
        }
    }

    @Test
    void unknownStartingWordFailsBeforeAnySentencesAreSaved() {
        Connection conn = connection(false);
        WordDAO unknownWords = new WordDAO() {
            @Override
            public Optional<Word> findByText(Connection connection, String text) {
                assertSame(conn, connection);
                return Optional.empty();
            }
        };
        var failure = assertThrows(IllegalArgumentException.class,
                () -> BatchGenerateController.generateBatch(3, "Most frequent", "unknown",
                        (saved, total) -> fail("No progress expected"), () -> conn,
                        unknownWords, new SentenceDAO() {
                            @Override
                            public GeneratedSentence insert(Connection connection, GeneratedSentence sentence) {
                                fail("No sentences should be saved");
                                return sentence;
                            }
                        }));
        assertTrue(failure.getMessage().contains("isn't in the database yet"));
        assertEquals(List.of("setAutoCommit", "close"), calls);
    }

    @Test
    void multipleStartingWordsAreRejectedBeforeOpeningConnection() {
        var failure = assertThrows(IllegalArgumentException.class,
                () -> BatchGenerateController.generateBatch(3, "Most frequent", "two words",
                        (saved, total) -> {}, () -> { fail("No connection expected"); return null; },
                        new WordDAO(), new SentenceDAO()));
        assertTrue(failure.getMessage().contains("Enter one starting word"));
    }

    @Test
    void connectionFailureIsThrownToTheTask() {
        SQLException failure = new SQLException("connection failed");
        assertSame(failure, assertThrows(SQLException.class,
                () -> BatchGenerateController.generateBatch(3, "Most frequent", (a, b) -> {},
                        () -> { throw failure; }, new WordDAO(), new SentenceDAO())));
    }

    @Test
    void emptyCorpusIsThrownWithoutReportingSaves() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> run(connection(false), true, 0));
        assertTrue(failure.getMessage().contains("Import some text first"));
        assertTrue(progress.isEmpty());
        assertEquals(List.of("setAutoCommit", "close"), calls);
    }

    @Test
    void firstInsertFailureIsThrownWithoutReportingSaves() {
        SQLException failure = assertThrows(SQLException.class,
                () -> run(connection(false), false, 1));
        assertEquals("insert failed", failure.getMessage());
        assertTrue(progress.isEmpty());
    }

    @Test
    void laterInsertFailureReturnsOnlyConfirmedSaves() throws Exception {
        var result = run(connection(false), false, 2);
        assertEquals(1, result.sentences().size());
        assertEquals(1, result.sentences().getFirst().getId());
        assertEquals("insert failed", result.failure().getMessage());
        assertEquals(List.of(1), progress);
    }

    @Test
    void closeFailureAfterAllSavesStillReturnsSuccess() throws Exception {
        var result = run(connection(true), false, 0);
        assertEquals(3, result.sentences().size());
        assertNull(result.failure());
        assertEquals(List.of(1, 2, 3), progress);
        assertEquals(List.of("setAutoCommit", "close"), calls);
    }

    @Test
    void closeFailureDoesNotHideThePartialSaveFailure() throws Exception {
        var result = run(connection(true), false, 2);
        assertEquals("insert failed", result.failure().getMessage());
        assertEquals("close failed", result.failure().getSuppressed()[0].getMessage());
        assertEquals(List.of(1), progress);
    }
}
