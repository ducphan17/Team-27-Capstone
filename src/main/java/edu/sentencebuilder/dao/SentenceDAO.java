package edu.sentencebuilder.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import edu.sentencebuilder.model.GeneratedSentence;

/**
 * Reads and writes the {@code generated_sentence} table.
 * Must only be called from background threads.
 */
public class SentenceDAO {

    /** Saves a generated sentence and sets its generated {@code id}. */
    public GeneratedSentence insert(GeneratedSentence s) throws SQLException {
        try (Connection conn = Database.getConnection()) {
            return insert(conn, s);
        }
    }

    /** Saves using the caller's connection without committing or closing it. */
    public GeneratedSentence insert(Connection conn, GeneratedSentence s) throws SQLException {
        String sql = "INSERT INTO generated_sentence (sentence, start_word, algorithm, added_to_corpus) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getSentence());
            ps.setString(2, s.getStartWord());
            ps.setString(3, s.getAlgorithm());
            ps.setBoolean(4, s.isAddedToCorpus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    s.setId(keys.getInt(1));
                }
            }
        }
        return s;
    }

/**
     * Loads every generated sentence, newest first.
     * Must not be called on the JavaFX Application Thread.
     *
     * @return all generated sentences, never null (empty if none exist)
     * @throws DataAccessException if the query fails
     */
    public List<GeneratedSentence> findAll() {
        String sql = "SELECT id, sentence, start_word, algorithm, created_at, added_to_corpus "
                + "FROM generated_sentence ORDER BY created_at DESC, id DESC";
        List<GeneratedSentence> sentences = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                sentences.add(mapRow(rs));
            }
            return sentences;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load generated sentences", e);
        }
    }

    /** Maps the current ResultSet row to a GeneratedSentence. */
    private static GeneratedSentence mapRow(ResultSet rs) throws SQLException {
        GeneratedSentence s = new GeneratedSentence();
        s.setId(rs.getInt("id"));
        s.setSentence(rs.getString("sentence"));
        s.setStartWord(rs.getString("start_word"));
        s.setAlgorithm(rs.getString("algorithm"));
        Timestamp created = rs.getTimestamp("created_at");
        s.setCreatedAt(created == null ? null : created.toLocalDateTime());
        s.setAddedToCorpus(rs.getBoolean("added_to_corpus"));
        return s;
    }

    //Aalvee
    /**
     * Marks the given sentences as added to the corpus (Batch Generate "Add to corpus").
     * Does nothing for a null or empty list.
     * Must not be called on the JavaFX Application Thread.
     *
     * @throws DataAccessException if the update fails
     */
    public void markAddedToCorpus(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        try (Connection conn = Database.getConnection()){
            markAddedToCorpus(conn, ids);
        } catch (SQLException e) {
            throw new DataAccessException("Could not mark sentences as added to corpus", e);
        }
    }

    //Uses the caller's connection without committing or closing it.
    //This lets corpus counts and sentence flags commit together.
    public void markAddedToCorpus(Connection conn, List<Integer> ids)
            throws SQLException {

        if (ids == null || ids.isEmpty()) {
            return;
        }

        String sql = """
                UPDATE generated_sentence
                SET added_to_corpus = TRUE
                WHERE id = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int id : ids) {
                ps.setInt(1, id);
                ps.addBatch();
            }

            ps.executeBatch();
        }
    }


}
