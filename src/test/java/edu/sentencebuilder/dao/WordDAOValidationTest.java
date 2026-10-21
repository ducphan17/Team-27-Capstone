package edu.sentencebuilder.dao;

import edu.sentencebuilder.model.Word;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WordDAOValidationTest {
    private final WordDAO words = new WordDAO();

    @Test
    void directInsertRejectsOversizedTextBeforeConnecting() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> words.insertIfAbsent("a".repeat(101)));
        assertTrue(error.getMessage().contains("100-character limit"));
    }

    @Test
    void batchRejectsOversizedTextBeforeUsingTheConnection() {
        // A null connection makes this independent of local database configuration:
        // validation must finish before even preparing the batch statement.
        assertThrows(IllegalArgumentException.class, () -> words.upsertCounts(null,
                Map.of("valid", 1, "a".repeat(101), 1), Map.of(), Map.of()));
    }

    @Test
    void updateRejectsOversizedTextBeforeConnecting() {
        Word word = new Word(1, "a".repeat(101), 1, 1, 1, null);
        assertThrows(IllegalArgumentException.class, () -> words.update(word));
    }
}
