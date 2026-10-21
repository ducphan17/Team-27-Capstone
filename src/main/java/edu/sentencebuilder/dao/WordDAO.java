package edu.sentencebuilder.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;

import edu.sentencebuilder.model.Word;

/**
 * Reads and writes the {@code word} table. All SQL uses PreparedStatement.
 * Must only be called from background threads, never the JavaFX Application Thread.
 */
public class WordDAO {

    /** Looks up a word by its exact (lowercase) text. */
    public Optional<Word> findByText(String text) throws SQLException {
        String sql = "SELECT word_id, text, total_count, start_count, end_count, first_seen FROM word WHERE text = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, text);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Returns every word ordered by text, for the Words Report tab.
     * The tab sorts and filters in memory, so one SELECT is enough.
     *
     * @throws DataAccessException if the query fails
     */
    public List<Word> findAll() {
        String sql = "SELECT word_id, text, total_count, start_count, end_count, first_seen "
                + "FROM word ORDER BY text ASC";
        List<Word> words = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                words.add(mapRow(rs));
            }
            return words;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load words", e);
        }
    }

/**
 * Adds a word with all counts 0 if it is not already stored, and returns its ID.
 * An existing row (counts, first_seen) is left untouched.
 * Must not be called on the JavaFX Application Thread.
 *
 * @throws DataAccessException if the insert or lookup fails
 */
public int insertIfAbsent(String text) {
    // Autocomplete bypasses the importer, so validate before opening its connection too.
    Word.validateTextLength(text);
    String insertSql = """
            INSERT INTO word (text) VALUES (?) AS incoming
            ON DUPLICATE KEY UPDATE text = word.text
            """;
    String lookupSql = "SELECT word_id FROM word WHERE text = ?";
    try (Connection conn = Database.getConnection()) {
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, text);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(lookupSql)) {
            ps.setString(1, text);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Word missing after insert: " + text);
                }
                return rs.getInt("word_id");
            }
        }
    } catch (SQLException e) {
        throw new DataAccessException("Could not add word '" + text + "'", e);
    }
}

    // Inserts new words or adds imported counts to existing words
    // Uses the caller's connection so the importer can save words, pairs, and
    // the file record in one transaction.
    public Map<String, Integer> upsertCounts(
            Connection conn,
            Map<String, Integer> totals,
            Map<String, Integer> starts,
            Map<String, Integer> ends) throws SQLException {
        // Check the complete batch before preparing or executing any writes.
        totals.keySet().forEach(Word::validateTextLength);
        
        String sql = """
                INSERT INTO word (text, total_count, start_count, end_count)
                VALUES (?, ?, ?, ?) AS incoming
                ON DUPLICATE KEY UPDATE
                    total_count = word.total_count + incoming.total_count,
                    start_count = word.start_count + incoming.start_count,
                    end_count = word.end_count + incoming.end_count
                """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> entry: totals.entrySet()) {
                String text = entry.getKey();

                ps.setString(1, text);
                ps.setInt(2, entry.getValue());

                //words absent from these maps did not start or end any sentences, so we can treat them as 0
                ps.setInt(3, starts.getOrDefault(text, 0));
                ps.setInt(4, ends.getOrDefault(text, 0));

                //queue one insert/update for each distinct word
                ps.addBatch();
            }

            ps.executeBatch();
        }

        Map<String, Integer> wordIds = new HashMap<>();

        //retrieve IDs for both newly inserted and existing words
        String lookupSql = "SELECT word_id FROM word WHERE text = ?";

        try (PreparedStatement ps = conn.prepareStatement(lookupSql)) {
            for (String text: totals.keySet()) {
                ps.setString(1, text);

                try (ResultSet rs = ps.executeQuery()) {
                    //using next() to return true when the word is found
                    //Throw an error only when no matching row exists
                    if (!rs.next()) {
                        throw new SQLException(
                            "Word missing after saving counts: " + text);
                    }

                    wordIds.put(text, rs.getInt("word_id"));
                }
            }
        }
        //retrieve IDs for both 

        return wordIds;
        

        }

    /**
     * Updates the editable fields of a word by ID (Words Report tab). Words are never deleted.
     * Must not be called on the JavaFX Application Thread.
     *
     * @throws DataAccessException if the update fails
     */
    public void update(Word word) {
        Word.validateTextLength(word.getText());
        String sql = "UPDATE word SET text = ?, total_count = ?, start_count = ?, end_count = ? "
                + "WHERE word_id = ?";
        try (Connection conn = Database.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, word.getText());
            ps.setInt(2, word.getTotalCount());
            ps.setInt(3, word.getStartCount());
            ps.setInt(4, word.getEndCount());
            ps.setInt(5, word.getWordId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update word " + word.getWordId(), e);
        }
    }

    static Word mapRow(ResultSet rs) throws SQLException {
        Timestamp firstSeen = rs.getTimestamp("first_seen");
        return new Word(
                rs.getInt("word_id"),
                rs.getString("text"),
                rs.getInt("total_count"),
                rs.getInt("start_count"),
                rs.getInt("end_count"),
                firstSeen == null ? null : firstSeen.toLocalDateTime());
    }
}
