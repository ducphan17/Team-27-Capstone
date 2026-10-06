package edu.sentencebuilder.parser;

import edu.sentencebuilder.dao.FileDAO;
import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.model.SourceFile;
import javafx.concurrent.Task;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        writeToDatabase(stats);

        fileDAO.insert(new SourceFile(file.getFileName().toString(), stats.wordCount, sentences.size()));
        updateProgress(totalSteps, totalSteps);
        updateMessage("Imported " + stats.wordCount + " words in " + sentences.size()
                + " sentences (" + stats.totals.size() + " distinct)");
        return null;
    }

    /**
     * TODO: write {@code stats} to MySQL with batched INSERT ... ON DUPLICATE KEY UPDATE.
     *
     * <p>ORDERING PROBLEM: word_pair stores word IDs (with foreign keys to word.word_id),
     * but a brand-new word has no ID until it is inserted. So the order must be:
     * <ol>
     *   <li>{@code Map<String,Integer> ids = wordDAO.upsertCounts(stats.totals, stats.starts, stats.ends)}
     *       — inserts/updates every word and returns text → word_id
     *       (re-SELECT the IDs afterwards; getGeneratedKeys is unreliable with ON DUPLICATE KEY UPDATE);</li>
     *   <li>{@code wordPairDAO.upsertCounts(stats.pairs, ids)} — now every pair can be mapped to IDs.</li>
     * </ol>
     * Do both in one transaction (setAutoCommit(false) / commit / rollback) so a failed import
     * doesn't leave words counted without their pairs. Call updateProgress() between batches.
     */
    private void writeToDatabase(Stats stats) throws Exception {
        // Intentionally left as a stub; the counting above already works.
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
