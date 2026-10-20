package edu.sentencebuilder.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.sql.Timestamp;
import java.util.ArrayList;

import edu.sentencebuilder.model.SourceFile;

/**
 * Reads and writes the {@code source_file} table (one row per imported file).
 * Must only be called from background threads.
 */
public class FileDAO {

    //this saves the imported filed record using the caller's connection
    // the caller controls when the entire import commits or rolls back
    public SourceFile insert(Connection conn, SourceFile file)
            throws SQLException {
        
        String sql = """
                INSERT INTO source_file (filename, word_count, sentence_count)
                VALUES (?, ?, ?)
                """;

        //close the statement when finished, but leave the shared connection open
        try(PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, file.getFilename());
            ps.setInt(2, file.getWordCount());
            ps.setInt(3, file.getSentenceCount());
            ps.executeUpdate();

            //store the database generated ID in the SourceFile object
            try(ResultSet keys = ps.getGeneratedKeys()) {
                if(keys.next()) {
                    file.setFileId(keys.getInt(1));
                }
            }
        }

        return file;
    }

    /**
     * Returns all imported-file records, newest first (import history).
     * Must not be called on the JavaFX Application Thread.
     *
     * @throws DataAccessException if the query fails
     */
    public List<SourceFile> findAll() {
        String sql = "SELECT file_id, filename, word_count, sentence_count, imported_at "
                + "FROM source_file ORDER BY imported_at DESC, file_id DESC";
        List<SourceFile> files = new ArrayList<>();
        try (Connection conn = Database.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                files.add(mapRow(rs));
            }
            return files;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load imported files", e);
        }
    }

    /** Maps the current ResultSet row to a SourceFile. */
    private static SourceFile mapRow(ResultSet rs) throws SQLException {
        SourceFile f = new SourceFile();
        f.setFileId(rs.getInt("file_id"));
        f.setFilename(rs.getString("filename"));
        f.setWordCount(rs.getInt("word_count"));
        f.setSentenceCount(rs.getInt("sentence_count"));
        Timestamp imported = rs.getTimestamp("imported_at");
        f.setImportedAt(imported == null ? null : imported.toLocalDateTime());
        return f;
    }
}
