// Tien Phan

package edu.sentencebuilder.parser;

import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Transaction outcomes of {@link AutocompleteService}, with in-memory fakes instead of MySQL. */
class AutocompleteServiceTransactionTest {

    /** Connection calls made by the service, e.g. "commit", "rollback", "close". */
    private final List<String> calls = new ArrayList<>();

    private Connection connection(boolean closeFails) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    if (method.getName().equals("close") && closeFails) {
                        throw new SQLException("Intentional close failure");
                    }
                    return null;
                });
    }

    private AutocompleteService service(boolean closeFails, boolean pairFails) {
        WordDAO words = new WordDAO() {
            @Override
            public int insertIfAbsent(Connection conn, String text) {
                return text.hashCode();
            }
        };
        WordPairDAO pairs = new WordPairDAO(words) {
            @Override
            public void incrementUserCount(Connection conn, int fromWordId, int toWordId) throws SQLException {
                calls.add("pair");
                if (pairFails) {
                    throw new SQLException("Intentional pair failure");
                }
            }
        };
        return new AutocompleteService(() -> connection(closeFails), words, pairs);
    }

    @Test
    void closeFailureAfterCommitIsNotReportedAsAFailedSave() {
        // A reported failure would be retried, counting every pair twice.
        assertDoesNotThrow(() -> service(true, false)
                .recordSentences(List.of(List.of("the", "cat", "sat"))));
        assertEquals(List.of("setAutoCommit", "pair", "pair", "commit", "close"), calls);
    }

    @Test
    void failureBeforeCommitRollsBackAndIsReported() {
        SQLException error = assertThrows(SQLException.class,
                () -> service(false, true).recordSentences(List.of(List.of("the", "cat"))));
        assertEquals("Intentional pair failure", error.getMessage());
        assertEquals(List.of("setAutoCommit", "pair", "rollback", "close"), calls);
    }
}
