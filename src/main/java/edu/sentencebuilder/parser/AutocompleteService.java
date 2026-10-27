// Tien Phan

package edu.sentencebuilder.parser;

import java.sql.Connection;
import java.sql.SQLException;

import edu.sentencebuilder.dao.Database;
import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.model.Word;

/**
 * Saves one word the user finished on the Autocomplete tab, independently of JavaFX:
 * adds unknown words to {@code word} (all counts 0) and adds 1 to
 * {@code word_pair.user_count} for the pair it completes. Imported counts are never
 * changed. Both writes commit together or not at all.
 */
public final class AutocompleteService {

    private final CorpusService.ConnectionFactory connections;
    private final WordDAO words;
    private final WordPairDAO pairs;

    public AutocompleteService() {
        this(Database::getConnection);
    }

    public AutocompleteService(CorpusService.ConnectionFactory connections) {
        this(connections, new WordDAO());
    }

    private AutocompleteService(CorpusService.ConnectionFactory connections, WordDAO words) {
        this(connections, words, new WordPairDAO(words));
    }

    // Tests inject DAO failures while exercising this exact transaction implementation.
    AutocompleteService(CorpusService.ConnectionFactory connections, WordDAO words, WordPairDAO pairs) {
        this.connections = connections;
        this.words = words;
        this.pairs = pairs;
    }

    /**
     * Records that {@code word} was typed or picked after {@code previousWord}.
     *
     * @param previousWord the word before it in the same sentence, or {@code null} at a
     *                     sentence start (then only an unknown {@code word} is added)
     * @throws IllegalArgumentException if either word exceeds the length limit; nothing is saved
     */
    public void recordWord(String previousWord, String word) throws SQLException {
        // Reject oversized words before opening a connection, as the importer does.
        Word.validateTextLength(word);
        if (previousWord != null) {
            Word.validateTextLength(previousWord);
        }

        try (Connection conn = connections.open()) {
            conn.setAutoCommit(false);
            try {
                // Text, not remembered IDs, is passed in, so each save looks up its own IDs
                // and never depends on an earlier save having finished.
                int wordId = words.insertIfAbsent(conn, word);
                if (previousWord != null) {
                    int previousId = words.insertIfAbsent(conn, previousWord);
                    pairs.incrementUserCount(conn, previousId, wordId);
                }
                conn.commit();
            } catch (SQLException | RuntimeException error) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            }
        }
    }
}
