package edu.sentencebuilder.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GenerateControllerTest {

    @Test
    void acceptsWholeNumbersInRange() {
        assertEquals(1, GenerateController.parseMaxWords("1"));
        assertEquals(20, GenerateController.parseMaxWords(" 20 "));
        assertEquals(GenerateController.MAX_WORDS_LIMIT,
                GenerateController.parseMaxWords(String.valueOf(GenerateController.MAX_WORDS_LIMIT)));
    }

    @Test
    void rejectsOutOfRange() {
        assertNull(GenerateController.parseMaxWords("0"));
        assertNull(GenerateController.parseMaxWords("-5"));
        assertNull(GenerateController.parseMaxWords(String.valueOf(GenerateController.MAX_WORDS_LIMIT + 1)));
    }

    @Test
    void rejectsNonNumbers() {
        assertNull(GenerateController.parseMaxWords("ten"));
        assertNull(GenerateController.parseMaxWords("2.5"));
        assertNull(GenerateController.parseMaxWords(""));
        assertNull(GenerateController.parseMaxWords(null));
    }
}
