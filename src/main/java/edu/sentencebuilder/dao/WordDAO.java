package edu.sentencebuilder.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import edu.sentencebuilder.model.ImportedWordCounts;
import edu.sentencebuilder.model.Word;

/**
 * Reads and writes the {@code word} table. All SQL uses PreparedStatement.
 * Must only be called from background threads, never the JavaFX Application Thread.
 */
public class WordDAO {
    //Aalvee
    /** Looks up a word by its exact (lowercase) text using a connection owned by this method. */
    public Optional<Word> findByText(String text) throws SQLException {
        try (Connection conn = Database.getConnection()) {
            return findByText(conn, text);
        }
    }

    /** Uses the caller's connection and leaves it open for further lookups. */
    public Optional<Word> findByText(Connection conn, String text)
            throws SQLException {

        String sql = """
                SELECT word_id, text, total_count, start_count,
                       end_count, first_seen
                FROM word
                WHERE text = ?
                """;

        // Close the statement and result set, but keep the shared connection open.
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, text);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(mapRow(rs))
                        : Optional.empty();
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
     * Tien Phan
     * 
     * Adds a word with all counts 0 if it is not already stored, and returns its ID.
     * An existing row (counts, first_seen) is left untouched.
     * Must not be called on the JavaFX Application Thread.
     *
     * @throws DataAccessException if the insert or lookup fails
     */
    public int insertIfAbsent(String text) {
        // Autocomplete bypasses the importer, so validate before opening its connection too.
        Word.validateTextLength(text);
        try (Connection conn = Database.getConnection()) {
            return insertIfAbsent(conn, text);
        } catch (SQLException e) {
            throw new DataAccessException("Could not add word '" + text + "'", e);
        }
    }

    /**
     * Same as {@link #insertIfAbsent(String)}, using the caller's connection so Autocomplete
     * can save a new word and its pair in one transaction. Leaves the connection open.
     */
    public int insertIfAbsent(Connection conn, String text) throws SQLException {
        Word.validateTextLength(text);
        String insertSql = """
                INSERT INTO word (text) VALUES (?) AS incoming
                ON DUPLICATE KEY UPDATE text = word.text
                """;
        String lookupSql = "SELECT word_id FROM word WHERE text = ?";
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
    }

    //Aalvee
    // Inserts new words or adds imported counts to existing words
    // Uses the caller's connection so the importer can save words, pairs, and
    // the file record in one transaction.
// Maximum number of words per IN (...) lookup. Well under MySQL's placeholder limit (65,535).
static final int LOOKUP_CHUNK_SIZE = 500;

public Map<String, Integer> upsertCounts(
        Connection conn,
        List<ImportedWordCounts> words) throws SQLException {
    // Check the complete batch before preparing or executing any writes.
    words.forEach(word -> Word.validateTextLength(word.text()));

    if (words.isEmpty()) {
        return new HashMap<>();
    }

    String sql = """
            INSERT INTO word (text, total_count, start_count, end_count)
            VALUES (?, ?, ?, ?) AS incoming
            ON DUPLICATE KEY UPDATE
                total_count = word.total_count + incoming.total_count,
                start_count = word.start_count + incoming.start_count,
                end_count = word.end_count + incoming.end_count
            """;

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        for (ImportedWordCounts word : words) {
            ps.setString(1, word.text());
            ps.setInt(2, word.totalCount());
            ps.setInt(3, word.startCount());
            ps.setInt(4, word.endCount());
            ps.addBatch();
        }
        ps.executeBatch();
    }

    // Keep a text-to-ID lookup so the importer can resolve word-pair references.
    List<String> texts = words.stream()
            .map(ImportedWordCounts::text)
            .distinct()
            .toList();
    return findIdsByText(conn, texts);
}

/**
 * Resolves word IDs in chunks of {@link #LOOKUP_CHUNK_SIZE} using IN (...) queries.
 * Uses the caller's connection and leaves it open.
 *
 * @throws SQLException if the query fails or any requested word has no row
 */
Map<String, Integer> findIdsByText(Connection conn, List<String> texts)
        throws SQLException {
    Map<String, Integer> wordIds = new HashMap<>(Math.max(16, (int) (texts.size() / 0.75f) + 1));

    for (int from = 0; from < texts.size(); from += LOOKUP_CHUNK_SIZE) {
        List<String> chunk = texts.subList(from, Math.min(from + LOOKUP_CHUNK_SIZE, texts.size()));

        String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
        String lookupSql = "SELECT word_id, text FROM word WHERE text IN (" + placeholders + ")";

        try (PreparedStatement ps = conn.prepareStatement(lookupSql)) {
            for (int i = 0; i < chunk.size(); i++) {
                ps.setString(i + 1, chunk.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    wordIds.put(rs.getString("text"), rs.getInt("word_id"));
                }
            }
        }
    }

    // Collation-insensitive matches (case, accents, trailing spaces) can return a row whose
    // stored text differs from the requested text. Resolve those individually so the
    // result still has an entry for every requested word.
    List<String> missing = texts.stream().filter(t -> !wordIds.containsKey(t)).toList();
    if (!missing.isEmpty()) {
        String singleSql = "SELECT word_id FROM word WHERE text = ?";
        try (PreparedStatement ps = conn.prepareStatement(singleSql)) {
            for (String text : missing) {
                ps.setString(1, text);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Word missing after saving counts: " + text);
                    }
                    wordIds.put(text, rs.getInt("word_id"));
                }
            }
        }
    }
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

    //Aalvee
    //loads words that have appeared at the beginning of an imported sentence
    //batch generate uses startCount as each words selection weight
    public List<Word> findStartingWords(){
        try (Connection conn = Database.getConnection()) {
            return findStartingWords(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Could not load starting words", e);
        }
    }

    /** Uses the caller's connection; isolated tests never need application credentials. */
    public List<Word> findStartingWords(Connection conn) throws SQLException {
        String sql = """
                SELECT word_id, text, total_count, start_count, end_count, first_seen
                FROM word
                WHERE start_count > 0
                ORDER BY text
                """;

        List<Word> words = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                words.add(mapRow(rs));
            }
            return words;
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
