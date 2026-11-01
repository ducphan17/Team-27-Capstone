package edu.sentencebuilder.dao;

import edu.sentencebuilder.generator.Follower;
import edu.sentencebuilder.model.Word;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies connection ownership and SQL error translation in the scoped generator source. */
class WordPairDAOSourceTest {
    private Connection connection() {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    throw new AssertionError("Adapter must not manage connection: " + method.getName());
                });
    }

    @Test
    void lookupsUseTheCallerConnectionWithoutClosingIt() {
        Connection conn = connection();
        Word word = new Word(1, "hello", 1, 1, 1, null);
        WordDAO words = new WordDAO() {
            @Override
            public Optional<Word> findByText(Connection actual, String text) {
                assertSame(conn, actual);
                assertEquals("hello", text);
                return Optional.of(word);
            }
        };
        List<Follower> followers = List.of(new Follower("world", 2, 1));
        WordPairDAO pairs = new WordPairDAO(words) {
            @Override
            public List<Follower> getFollowers(Connection actual, String text) {
                assertSame(conn, actual);
                assertEquals("hello", text);
                return followers;
            }
        };
        var source = pairs.using(conn);
        assertSame(word, source.getWord("hello").orElseThrow());
        assertSame(followers, source.getFollowers("hello"));
    }

    @Test
    void sqlFailuresKeepTheirCauseAndLookupContext() {
        SQLException failure = new SQLException("lookup failed");
        WordDAO words = new WordDAO() {
            @Override
            public Optional<Word> findByText(Connection conn, String text) throws SQLException {
                throw failure;
            }
        };
        WordPairDAO pairs = new WordPairDAO(words) {
            @Override
            public List<Follower> getFollowers(Connection conn, String text) throws SQLException {
                throw failure;
            }
        };
        var source = pairs.using(connection());
        var wordError = assertThrows(DataAccessException.class, () -> source.getWord("hello"));
        assertEquals("Could not load word 'hello'", wordError.getMessage());
        assertSame(failure, wordError.getCause());
        var pairError = assertThrows(DataAccessException.class, () -> source.getFollowers("hello"));
        assertEquals("Could not load followers of 'hello'", pairError.getMessage());
        assertSame(failure, pairError.getCause());
    }
}
