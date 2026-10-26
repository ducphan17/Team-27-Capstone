//Aalvee
package edu.sentencebuilder.parser;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.sentencebuilder.dao.FileDAO;
import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.model.ImportedWordCounts;
import edu.sentencebuilder.model.SourceFile;
import edu.sentencebuilder.model.Word;
import edu.sentencebuilder.model.WordPair;

/** Shared counting and persistence for file imports and corpus feedback, without JavaFX. */
public class TextImporter {
    private final Tokenizer tokenizer;
    private final WordDAO wordDAO;
    private final WordPairDAO wordPairDAO;
    private final FileDAO fileDAO;

    public TextImporter() {
        this(new Tokenizer(), new WordDAO(), new WordPairDAO(new WordDAO()), new FileDAO());
    }

    public TextImporter(Tokenizer tokenizer, WordDAO wordDAO,
                        WordPairDAO wordPairDAO, FileDAO fileDAO) {
        this.tokenizer = tokenizer;
        this.wordDAO = wordDAO;
        this.wordPairDAO = wordPairDAO;
        this.fileDAO = fileDAO;
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
            throw new InvalidImportException(
                    "There are no words to add to the corpus.");
        }

        // Reuse the same validation as ordinary file imports.
        validateWordLengths(stats);
        saveCounts(conn, stats, sentences.size(), sourceName);
    }

    /** Shared persistence logic for file imports and generated text. */
    void saveCounts(
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
