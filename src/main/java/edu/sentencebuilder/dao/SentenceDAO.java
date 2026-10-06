package edu.sentencebuilder.dao;

import edu.sentencebuilder.model.GeneratedSentence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Reads and writes the {@code generated_sentence} table.
 * Must only be called from background threads.
 */
public class SentenceDAO {

    /** Saves a generated sentence and sets its generated {@code id}. */
    public GeneratedSentence insert(GeneratedSentence s) throws SQLException {
        String sql = "INSERT INTO generated_sentence (sentence, start_word, algorithm, added_to_corpus) VALUES (?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
     * TODO: return all sentences, newest first (Sentence History tab).
     * Duplicates can be found in SQL (GROUP BY sentence HAVING COUNT(*) > 1)
     * or in Java with a Map&lt;String, Integer&gt; of counts.
     */
    public List<GeneratedSentence> findAll() throws SQLException {
        throw new UnsupportedOperationException("TODO");
    }

    /** TODO: set added_to_corpus = TRUE for the given IDs (Batch Generate "Add to corpus"). */
    public void markAddedToCorpus(List<Integer> ids) throws SQLException {
        throw new UnsupportedOperationException("TODO");
    }
}
