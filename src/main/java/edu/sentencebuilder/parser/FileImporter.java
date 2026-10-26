package edu.sentencebuilder.parser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import edu.sentencebuilder.dao.Database;
import edu.sentencebuilder.dao.FileDAO;
import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.parser.TextImporter.Stats;
import javafx.concurrent.Task;

/**
 * Background task that imports one text file: tokenizes it, counts word, pair,
 * start and end statistics in memory, then writes them to the database.
 * Run it on a worker thread; bind a ProgressBar to {@link #progressProperty()}
 * and a Label to {@link #messageProperty()}.
 */
public class FileImporter extends Task<Void> {

    /** A JDBC connection factory may throw SQLException, unlike Supplier<Connection>. */
    @FunctionalInterface
    interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    private final ConnectionFactory connections;
    private final Path file;
    private final Tokenizer tokenizer;
    private final TextImporter textImporter;

    public FileImporter(Path file, Tokenizer tokenizer, WordDAO wordDAO, WordPairDAO wordPairDAO, FileDAO fileDAO) {
        this(file, tokenizer, wordDAO, wordPairDAO, fileDAO, Database::getConnection);
    }

    // Tests supply isolated connections; the UI keeps using the original constructor.
    FileImporter(Path file, Tokenizer tokenizer, WordDAO wordDAO, WordPairDAO wordPairDAO,
                 FileDAO fileDAO, ConnectionFactory connections) {
        this.connections = connections;
        this.file = file;
        this.tokenizer = tokenizer;
        this.textImporter = new TextImporter(tokenizer, wordDAO, wordPairDAO, fileDAO);
    }

    @Override
    protected Void call() throws Exception {
        updateMessage("Reading " + file.getFileName() + "...");
        String text = Files.readString(file, StandardCharsets.UTF_8);
        List<List<String>> sentences = tokenizer.tokenize(text);

        updateMessage("Counting words...");
        Stats stats = new Stats();
        // Counting is roughly the first half of the work; the DB write is the second half.
        long totalSteps = sentences.size() * 2L + 1;
        for (int i = 0; i < sentences.size(); i++) {
            if (isCancelled()) {
                updateMessage("Cancelled");
                return null;
            }
            stats.addSentence(sentences.get(i));
            updateProgress(i + 1, totalSteps);
        }

        // Reject bad input before announcing the save phase. The transaction entry point
        // repeats this guard so direct callers receive the same protection.
        TextImporter.validateWordLengths(stats);
        updateMessage("Saving to database...");
        // Save words, pairs, and file metadata together.
        writeToDatabase(stats, sentences.size());

        updateProgress(totalSteps, totalSteps);
        updateMessage("Imported " + stats.wordCount + " words in " + sentences.size()
                + " sentences (" + stats.words.size() + " distinct)");
        return null;
    }

    //Aalvee
    // Package access preserves the existing import tests.
    void writeToDatabase(Stats stats, int sentenceCount) throws SQLException {
        // Reject oversized words before opening a database connection.
        TextImporter.validateWordLengths(stats);

        try (Connection conn = connections.open()) {
            conn.setAutoCommit(false);

            try {
                textImporter.saveCounts(
                        conn,
                        stats,
                        sentenceCount,
                        file.getFileName().toString());

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

    /** Imports text without taking ownership of the caller's transaction. */
    public void importText(Connection conn, String text, String sourceName) throws SQLException {
        textImporter.importText(conn, text, sourceName);
    }
}
