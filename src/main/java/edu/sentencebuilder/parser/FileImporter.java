package edu.sentencebuilder.parser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.sentencebuilder.dao.Database;
import edu.sentencebuilder.dao.FileDAO;
import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.model.SourceFile;
import edu.sentencebuilder.model.ImportedWordCounts;
import edu.sentencebuilder.model.Word;
import edu.sentencebuilder.model.WordPair;
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
    private final WordDAO wordDAO;
    private final WordPairDAO wordPairDAO;
    private final FileDAO fileDAO;

    public FileImporter(Path file, Tokenizer tokenizer, WordDAO wordDAO, WordPairDAO wordPairDAO, FileDAO fileDAO) {
        this(file, tokenizer, wordDAO, wordPairDAO, fileDAO, Database::getConnection);
    }

    // Tests supply isolated connections; the UI keeps using the original constructor.
    FileImporter(Path file, Tokenizer tokenizer, WordDAO wordDAO, WordPairDAO wordPairDAO,
                 FileDAO fileDAO, ConnectionFactory connections) {
        this.connections = connections;
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

        // Reject bad input before announcing the save phase. The transaction entry point
        // repeats this guard so direct callers receive the same protection.
        validateWordLengths(stats);
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
        validateWordLengths(stats);

        try (Connection conn = connections.open()) {
            conn.setAutoCommit(false);

            try {
                saveCounts(
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

    /**
     * Imports text using a transaction owned by the caller.
     * Does not commit, roll back, or close the supplied connection.
     */
    public void importText(Connection conn, String text, String sourceName)
            throws SQLException {

        List<List<String>> sentences = tokenizer.tokenize(text);
        Stats stats = new Stats();

        for (List<String> sentence : sentences) {
            stats.addSentence(sentence);
        }

        if (sentences.isEmpty()) {
            throw new IllegalArgumentException(
                    "There are no words to add to the corpus.");
        }

        // Reuse the same validation as ordinary file imports.
        validateWordLengths(stats);
        saveCounts(conn, stats, sentences.size(), sourceName);
    }

    /** Shared persistence logic for file imports and generated text. */
    private void saveCounts(
            Connection conn,
            Stats stats,
            int sentenceCount,
            String sourceName) throws SQLException {

        // Save words first because word pairs need their database IDs.
        Map<String, Integer> wordIds = wordDAO.upsertCounts(
                conn, stats.toWordCounts());

        wordPairDAO.upsertCounts(conn, stats.toWordPairs(wordIds));

        // Record where these imported counts came from.
        SourceFile source = new SourceFile(
                sourceName, stats.wordCount, sentenceCount);

        fileDAO.insert(conn, source);
    }

    static void validateWordLengths(Stats stats) {
        for (String word : stats.words.keySet()) {
            try {
                Word.validateTextLength(word);
            } catch (IllegalArgumentException error) {
                // Keep import context out of the shared model/DAO validation message.
                throw new InvalidImportException(error.getMessage() + " No data was imported.", error);
            }
        }
    }

    /** In-memory counts gathered from one import, keyed by word text. */
    static final class Stats {
        // The map finds a word; its object keeps all three counts together.
        final Map<String, WordCounts> words = new HashMap<>();
        /** Lookup table for counting repeated pairs: from-word → (to-word → count). */
        final Map<String, Map<String, Integer>> pairs = new HashMap<>();
        int wordCount;

        // Mutable counters avoid allocating a replacement record for every occurrence.
        private static final class WordCounts {
            int totalCount;
            int startCount;
            int endCount;
        }

        // Snapshot the counters into immutable records for the DAO.
        List<ImportedWordCounts> toWordCounts() {
            List<ImportedWordCounts> result = new ArrayList<>(words.size());
            for (Map.Entry<String, WordCounts> entry : words.entrySet()) {
                WordCounts counts = entry.getValue();
                result.add(new ImportedWordCounts(entry.getKey(), counts.totalCount,
                        counts.startCount, counts.endCount));
            }
            return result;
        }

        // Convert the counting lookups into records after the words have database IDs.
        List<WordPair> toWordPairs(Map<String, Integer> wordIds) throws SQLException {
            List<WordPair> result = new ArrayList<>();
            for (Map.Entry<String, Map<String, Integer>> fromEntry : pairs.entrySet()) {
                Integer fromId = wordIds.get(fromEntry.getKey());
                if (fromId == null) {
                    throw new SQLException("Missing word ID: " + fromEntry.getKey());
                }
                for (Map.Entry<String, Integer> toEntry : fromEntry.getValue().entrySet()) {
                    Integer toId = wordIds.get(toEntry.getKey());
                    if (toId == null) {
                        throw new SQLException("Missing word ID: " + toEntry.getKey());
                    }
                    // Imported pairs contribute to count; userCount starts at zero.
                    result.add(new WordPair(fromId, toId, toEntry.getValue(), 0));
                }
            }
            return result;
        }

        void addSentence(List<String> words) {
            if (words.isEmpty()) {
                return;
            }
            for (int i = 0; i < words.size(); i++) {
                WordCounts counts = this.words.computeIfAbsent(words.get(i), key -> new WordCounts());
                counts.totalCount++;
                // A single-word sentence contributes to both boundary counts.
                if (i == 0) {
                    counts.startCount++;
                }
                if (i == words.size() - 1) {
                    counts.endCount++;
                }
                if (i + 1 < words.size()) {
                    pairs.computeIfAbsent(words.get(i), k -> new HashMap<>())
                            .merge(words.get(i + 1), 1, Integer::sum);
                }
            }
            wordCount += words.size();
        }
    }
}
