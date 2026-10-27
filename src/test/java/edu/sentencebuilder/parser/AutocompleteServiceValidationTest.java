// Tien Phan

package edu.sentencebuilder.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AutocompleteServiceValidationTest {

    // The factory fails the test if called: validation must finish before a connection opens.
    private final AutocompleteService service = new AutocompleteService(() -> {
        throw new AssertionError("Opened a connection for an invalid word");
    });

    @Test
    void rejectsOversizedWordBeforeConnecting() {
        assertThrows(IllegalArgumentException.class, () -> service.recordWord("the", "a".repeat(101)));
    }

    @Test
    void rejectsOversizedPreviousWordBeforeConnecting() {
        assertThrows(IllegalArgumentException.class, () -> service.recordWord("a".repeat(101), "the"));
    }
}
