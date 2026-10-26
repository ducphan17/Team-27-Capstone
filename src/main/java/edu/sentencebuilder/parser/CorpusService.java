//Aalvee
package edu.sentencebuilder.parser;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import edu.sentencebuilder.dao.Database;
import edu.sentencebuilder.dao.SentenceDAO;

/** Owns the complete Add selected to corpus transaction, independently of JavaFX. */
public final class CorpusService {
    @FunctionalInterface
    public interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    private final ConnectionFactory connections;
    private final TextImporter importer;
    private final SentenceDAO sentences;

    public CorpusService() {
        this(Database::getConnection);
    }

    public CorpusService(ConnectionFactory connections) {
        this(connections, new TextImporter(), new SentenceDAO());
    }

    // Tests inject DAO failures while exercising this exact transaction implementation.
    CorpusService(ConnectionFactory connections, TextImporter importer, SentenceDAO sentences) {
        this.connections = connections;
        this.importer = importer;
        this.sentences = sentences;
    }

    public void addToCorpus(String text, List<Integer> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        try (Connection conn = connections.open()) {
            conn.setAutoCommit(false);
            try {
                importer.importText(conn, text, "batch-generated");
                sentences.markAddedToCorpus(conn, ids);
                conn.commit();
            } catch (SQLException | RuntimeException error) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            }
        }
    }
}
