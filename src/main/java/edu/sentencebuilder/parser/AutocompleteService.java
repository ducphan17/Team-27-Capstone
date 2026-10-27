// Tien Phan

package edu.sentencebuilder.parser;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import edu.sentencebuilder.dao.Database;
import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.model.Word;

/**
 * Saves sentences the user finished on the Autocomplete tab, independently of JavaFX:
 * adds unknown words to {@code word} (all counts 0) and adds 1 to
 * {@code word_pair.user_count} for each pair of neighboring words. Imported counts are
 * never changed. All writes for one call commit together or not at all.
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
     * Records finished sentences: every word is added if unknown, and each pair of
     * neighboring words in a sentence gets 1 more {@code user_count}. Pairs never cross
     * from one sentence into the next, matching imported pairs.
     *
     * @param sentences one list of lowercase words per sentence; empty lists are skipped
     * @throws IllegalArgumentException if any word exceeds the length limit; nothing is saved
     * @throws SQLException only if nothing was committed, so retrying never counts twice
     */
    public void recordSentences(List<List<String>> sentences) throws SQLException {
        // Reject oversized words before opening a connection, as the importer does.
        sentences.forEach(sentence -> sentence.forEach(Word::validateTextLength));
        if (sentences.stream().allMatch(List::isEmpty)) {
            return;
        }

        Connection conn = connections.open();
        try {
            conn.setAutoCommit(false);
            for (List<String> sentence : sentences) {
                // Words are saved before pairs, because pairs need word IDs.
                Integer previousId = null;
                for (String word : sentence) {
                    int wordId = words.insertIfAbsent(conn, word);
                    if (previousId != null) {
                        pairs.incrementUserCount(conn, previousId, wordId);
                    }
                    previousId = wordId;
                }
            }
            conn.commit();
        } catch (SQLException | RuntimeException error) {
            try {
                conn.rollback();
            } catch (SQLException rollbackError) {
                error.addSuppressed(rollbackError);
            }
            try {
                conn.close();
            } catch (SQLException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
        // The counts are committed. A failure to close must not report a failed save:
        // the caller would retry it and count every pair twice.
        try {
            conn.close();
        } catch (SQLException ignored) {
            // Nothing to undo; the driver discards the broken connection.
        }
    }
}
