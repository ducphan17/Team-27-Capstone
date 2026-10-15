package edu.sentencebuilder.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

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

    /** TODO: return all imported files, newest first (e.g. to show import history). */
    public List<SourceFile> findAll() throws SQLException {
        throw new UnsupportedOperationException("TODO");
    }
}
