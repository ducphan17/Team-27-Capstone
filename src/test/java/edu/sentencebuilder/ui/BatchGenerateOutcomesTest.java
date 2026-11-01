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
