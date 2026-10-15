package edu.sentencebuilder.parser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.sentencebuilder.dao.Database;
import edu.sentencebuilder.dao.FileDAO;
import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.model.SourceFile;
import javafx.concurrent.Task;

/**
 * Background task that imports one text file: tokenizes it, counts word, pair,
 * start and end statistics in memory, then writes them to the database.
 * Run it on a worker thread; bind a ProgressBar to {@link #progressProperty()}
 * and a Label to {@link #messageProperty()}.
 */
public class FileImporter extends Task<Void> {

    private final Path file;
    private final Tokenizer tokenizer;
    private final WordDAO wordDAO;
    private final WordPairDAO wordPairDAO;
    private final FileDAO fileDAO;

    public FileImporter(Path file, Tokenizer tokenizer, WordDAO wordDAO, WordPairDAO wordPairDAO, FileDAO fileDAO) {
        this.file = file;
        this.tokenizer = tokenizer;
        this.wordDAO = wordDAO;
        this.wordPairDAO = wordPairDAO;
        this.fileDAO = fileDAO;
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

        updateMessage("Saving to database...");
        // Save words, pairs, and file metadata together.
        writeToDatabase(stats, sentences.size());

        updateProgress(totalSteps, totalSteps);
        updateMessage("Imported " + stats.wordCount + " words in " + sentences.size()
                + " sentences (" + stats.totals.size() + " distinct)");
        return null;
    }

    //this Saves the entire import in one transaction: all records are saved,
    //or the changes are rolled back if an operation fails.
    private void writeToDatabase(Stats stats, int sentenceCount) throws SQLException {
        // This method owns the shared connection and closes it when finished.
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Save words first so every pair can reference existing word IDs.
                Map<String, Integer> wordIds = wordDAO.upsertCounts(
                        conn, stats.totals, stats.starts, stats.ends);
                wordPairDAO.upsertCounts(conn, stats.pairs, wordIds);

                // Include the file record in the same transaction.
                SourceFile sourceFile = new SourceFile(
                        file.getFileName().toString(), stats.wordCount, sentenceCount);
                fileDAO.insert(conn, sourceFile);

                // Make the complete import permanent only after all saves succeed.
                conn.commit();
            } catch (SQLException | RuntimeException error) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    // Preserve the original failure if rollback also fails.
                    error.addSuppressed(rollbackError);
                }
                // The task's failure handler displays this error in the Import tab.
                throw error;
            }
        }
    }

    /** In-memory counts gathered from one import, keyed by word text. */
    static final class Stats {
        final Map<String, Integer> totals = new HashMap<>();
        final Map<String, Integer> starts = new HashMap<>();
        final Map<String, Integer> ends = new HashMap<>();
        /** from-word → (to-word → count) */
        final Map<String, Map<String, Integer>> pairs = new HashMap<>();
        int wordCount;

        void addSentence(List<String> words) {
            if (words.isEmpty()) {
                return;
            }
            starts.merge(words.getFirst(), 1, Integer::sum);
            ends.merge(words.getLast(), 1, Integer::sum);
            for (int i = 0; i < words.size(); i++) {
                totals.merge(words.get(i), 1, Integer::sum);
                if (i + 1 < words.size()) {
                    pairs.computeIfAbsent(words.get(i), k -> new HashMap<>())
                            .merge(words.get(i + 1), 1, Integer::sum);
                }
            }
            wordCount += words.size();
        }
    }
}
