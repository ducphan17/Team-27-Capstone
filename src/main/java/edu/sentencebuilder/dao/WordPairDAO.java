package edu.sentencebuilder.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import edu.sentencebuilder.generator.Follower;
import edu.sentencebuilder.generator.WordStatsSource;
import edu.sentencebuilder.model.Word;
import edu.sentencebuilder.model.WordPair;

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
        // Tien Phan
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

    // Saves imported word-pair records using the caller's transaction.
    // The importer resolves word IDs before calling this method.
    public void upsertCounts(
            Connection conn,
            List<WordPair> pairs) throws SQLException {

        String sql = """
                INSERT INTO word_pair (from_word_id, to_word_id, count)
                VALUES (?, ?, ?) AS incoming
                ON DUPLICATE KEY UPDATE
                    count = word_pair.count + incoming.count
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Each object supplies one database row; imports leave user_count unchanged.
            for (WordPair pair : pairs) {
                ps.setInt(1, pair.getFromWordId());
                ps.setInt(2, pair.getToWordId());
                ps.setInt(3, pair.getCount());
                ps.addBatch();
            }

            ps.executeBatch();
        }
    }
    

    /**
     * Adds 1 to user_count for the pair, creating the row (count 0) if needed.
     * Must not be called on the JavaFX Application Thread.
     *
     * @throws DataAccessException if the upsert fails
     */
    public void incrementUserCount(int fromWordId, int toWordId) {
        try (Connection conn = Database.getConnection()) {
            incrementUserCount(conn, fromWordId, toWordId);
        } catch (SQLException e) {
            throw new DataAccessException(
                    "Could not increment user count for pair " + fromWordId + " -> " + toWordId, e);
        }
    }

    /**
     * Same as {@link #incrementUserCount(int, int)}, using the caller's connection so the
     * Autocomplete save is one transaction. Leaves the connection open.
     */
    public void incrementUserCount(Connection conn, int fromWordId, int toWordId) throws SQLException {
        String sql = """
                INSERT INTO word_pair (from_word_id, to_word_id, count, user_count)
                VALUES (?, ?, 0, 1) AS incoming
                ON DUPLICATE KEY UPDATE user_count = word_pair.user_count + 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fromWordId);
            ps.setInt(2, toWordId);
            ps.executeUpdate();
        }
    }
}
