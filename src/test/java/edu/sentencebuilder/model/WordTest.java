package edu.sentencebuilder.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WordTest {
    @Test
    void acceptsOneHundredCharacters() {
        assertDoesNotThrow(() -> Word.validateTextLength("a".repeat(100)));
    }

    @Test
    void rejectsOneHundredAndOneCharactersWithShortPreview() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> Word.validateTextLength("a".repeat(101)));
        assertTrue(error.getMessage().contains("100-character limit (101 characters)"));
        assertFalse(error.getMessage().contains("a".repeat(101)));
        // This validator also serves direct writes, so it must not claim an import failed.
        assertFalse(error.getMessage().contains("imported"));
    }

    @Test
    void countsUnicodeCodePointsRatherThanUtf16Units() {
        String letter = new String(Character.toChars(0x10428));
        assertDoesNotThrow(() -> Word.validateTextLength(letter.repeat(100)));
        assertThrows(IllegalArgumentException.class,
                () -> Word.validateTextLength(letter.repeat(101)));
    }
}
