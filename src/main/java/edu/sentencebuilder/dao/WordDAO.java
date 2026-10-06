package edu.sentencebuilder.dao;

import edu.sentencebuilder.model.Word;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * TODO: return every word, ordered by text. Used by the Words Report tab.
     * The tab sorts/filters in memory, so one SELECT is enough.
     */
    public List<Word> findAll() throws SQLException {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * TODO: insert a single word with all counts 0 (Autocomplete adds unknown words this way).
     * Use INSERT IGNORE or ON DUPLICATE KEY UPDATE so it is safe if the word already exists,
     * and return the word's ID (getGeneratedKeys, or SELECT afterwards).
     */
    public int insertIfAbsent(String text) throws SQLException {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * TODO: batch upsert counts from an import, in one transaction:
     * INSERT INTO word (text, total_count, start_count, end_count) VALUES (?, ?, ?, ?) AS new
     * ON DUPLICATE KEY UPDATE total_count = word.total_count + new.total_count, ...
     * (MySQL 8.0.19+ row-alias syntax; the older VALUES(col) form is deprecated.)
     * Use addBatch()/executeBatch(); consider adding rewriteBatchedStatements=true to db.url.
     *
     * @return map from word text to word_id, needed afterwards to insert word_pair rows
     */
    public Map<String, Integer> upsertCounts(Map<String, Integer> totals,
                                             Map<String, Integer> starts,
                                             Map<String, Integer> ends) throws SQLException {
        throw new UnsupportedOperationException("TODO");
    }

    /** TODO: UPDATE the editable fields of a word (Words Report tab). No deletes allowed. */
    public void update(Word word) throws SQLException {
        throw new UnsupportedOperationException("TODO");
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
