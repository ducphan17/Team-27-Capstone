package edu.sentencebuilder.dao;

import edu.sentencebuilder.model.SourceFile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Reads and writes the {@code source_file} table (one row per imported file).
 * Must only be called from background threads.
 */
public class FileDAO {

    /** Inserts a file record and sets its generated {@code fileId}. */
    public SourceFile insert(SourceFile file) throws SQLException {
        String sql = "INSERT INTO source_file (filename, word_count, sentence_count) VALUES (?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, file.getFilename());
            ps.setInt(2, file.getWordCount());
            ps.setInt(3, file.getSentenceCount());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    file.setFileId(keys.getInt(1));
                }
            }
        }
        return file;
    }

    /** TODO: return all imported files, newest first (e.g. to show import history). */
    public List<SourceFile> findAll() throws SQLException {
        throw new UnsupportedOperationException("TODO");
    }
}
