package edu.sentencebuilder.dao;

import edu.sentencebuilder.generator.Follower;
import edu.sentencebuilder.generator.WordStatsSource;
import edu.sentencebuilder.model.Word;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes the {@code word_pair} table, and is the database-backed
 * {@link WordStatsSource} that generators use. SQL errors are rethrown as
 * {@link DataAccessException} because the interface methods cannot throw SQLException.
 * Must only be called from background threads.
 */
public class WordPairDAO implements WordStatsSource {

    private final WordDAO wordDAO;

    public WordPairDAO(WordDAO wordDAO) {
        this.wordDAO = wordDAO;
    }

    @Override
    public List<Follower> getFollowers(String word) {
        String sql = """
                SELECT t.text, p.count, p.user_count
                FROM word_pair p
                JOIN word f ON f.word_id = p.from_word_id
                JOIN word t ON t.word_id = p.to_word_id
                WHERE f.text = ?
                ORDER BY p.count + p.user_count DESC
                """;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, word);
            List<Follower> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Follower(rs.getString(1), rs.getInt(2), rs.getInt(3)));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load followers of '" + word + "'", e);
        }
    }

    @Override
    public Optional<Word> getWord(String text) {
        try {
            return wordDAO.findByText(text);
        } catch (SQLException e) {
            throw new DataAccessException("Could not load word '" + text + "'", e);
        }
    }

    /**
     * TODO: batch upsert pair counts from an import:
     * INSERT INTO word_pair (from_word_id, to_word_id, count) VALUES (?, ?, ?) AS new
     * ON DUPLICATE KEY UPDATE count = word_pair.count + new.count
     * Needs word IDs, so call this only after WordDAO.upsertCounts has run.
     *
     * @param pairCounts from-word text → (to-word text → count)
     * @param wordIds    word text → word_id, as returned by WordDAO.upsertCounts
     */
    public void upsertCounts(Map<String, Map<String, Integer>> pairCounts,
                             Map<String, Integer> wordIds) throws SQLException {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * TODO: add 1 to user_count for (fromWordId, toWordId), creating the row if needed:
     * INSERT INTO word_pair (from_word_id, to_word_id, user_count) VALUES (?, ?, 1)
     * ON DUPLICATE KEY UPDATE user_count = user_count + 1
     */
    public void incrementUserCount(int fromWordId, int toWordId) throws SQLException {
        throw new UnsupportedOperationException("TODO");
    }
}
