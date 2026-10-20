package edu.sentencebuilder.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import edu.sentencebuilder.generator.Follower;
import edu.sentencebuilder.generator.WordStatsSource;
import edu.sentencebuilder.model.Word;

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

    //Saves imported word-pair counts using the callers connection
    //call this after saving words so every word already has an ID
    public void upsertCounts(
            Connection conn,
            Map<String, Map<String, Integer>> pairCounts,
            Map<String, Integer> wordIds) throws SQLException {

        String sql = """
                INSERT INTO word_pair (from_word_id, to_word_id, count)
                VALUES (?, ?, ?) AS incoming
                ON DUPLICATE KEY UPDATE
                    count = word_pair.count + incoming.count
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            //Each outer entry represents one word and all its followers
            for (Map.Entry<String, Map<String, Integer>> fromEntry
                    : pairCounts.entrySet()) {
                Integer fromId = wordIds.get(fromEntry.getKey());

                if (fromId == null) {
                    throw new SQLException(
                        "Missing word ID: " + fromEntry.getKey());
                }

                //Each inner entry represents one follower of the outer word 
                for (Map.Entry<String, Integer> toEntry
                        : fromEntry.getValue().entrySet()){
                    Integer toId = wordIds.get(toEntry.getKey());

                    if (toId == null) {
                        throw new SQLException(
                            "Missing word ID: " + toEntry.getKey());
                    }

                    ps.setInt(1, fromId);
                    ps.setInt(2, toId);
                    ps.setInt(3, toEntry.getValue());
                    ps.addBatch();
                }         
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
        String sql = """
                INSERT INTO word_pair (from_word_id, to_word_id, count, user_count)
                VALUES (?, ?, 0, 1) AS incoming
                ON DUPLICATE KEY UPDATE user_count = word_pair.user_count + 1
                """;
        try (Connection conn = Database.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fromWordId);
            ps.setInt(2, toWordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException(
                    "Could not increment user count for pair " + fromWordId + " -> " + toWordId, e);
        }
    }
}
