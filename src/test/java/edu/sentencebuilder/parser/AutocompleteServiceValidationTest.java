// Tien Phan

package edu.sentencebuilder.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutocompleteServiceValidationTest {

    // The factory fails the test if called: validation must finish before a connection opens.
    private final AutocompleteService service = new AutocompleteService(() -> {
        throw new AssertionError("Opened a connection");
    });

    @Test
    void rejectsAnOversizedWordInAnySentenceBeforeConnecting() {
        assertThrows(IllegalArgumentException.class, () -> service.recordSentences(
                List.of(List.of("the", "cat"), List.of("a".repeat(101)))));
    }

    @Test
    void nothingToSaveNeverConnects() {
        assertDoesNotThrow(() -> service.recordSentences(List.of()));
        assertDoesNotThrow(() -> service.recordSentences(List.of(List.of())));
    }
}
