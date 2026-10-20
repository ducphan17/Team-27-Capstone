package edu.sentencebuilder.ui;

import edu.sentencebuilder.model.Word;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordsReportControllerTest {

    private static Word word(String text) {
        return new Word(1, text, 5, 1, 1, null);
    }

    @Test
    void blankFilterMatchesEverything() {
        assertTrue(WordsReportController.matchesFilter(word("cat"), null));
        assertTrue(WordsReportController.matchesFilter(word("cat"), "   "));
    }

    @Test
    void filterIsCaseInsensitiveSubstringMatch() {
        assertTrue(WordsReportController.matchesFilter(word("concatenate"), "CAT"));
        assertTrue(WordsReportController.matchesFilter(word("cat"), " cat "));
        assertFalse(WordsReportController.matchesFilter(word("dog"), "cat"));
    }

    @Test
    void validCountsPass() {
        assertNull(WordsReportController.validateCounts(10, 3, 4));
        assertNull(WordsReportController.validateCounts(0, 0, 0));
        assertNull(WordsReportController.validateCounts(5, 5, 5));
    }

    @Test
    void negativeCountsAreRejected() {
        assertNotNull(WordsReportController.validateCounts(-1, 0, 0));
        assertNotNull(WordsReportController.validateCounts(5, -1, 0));
        assertNotNull(WordsReportController.validateCounts(5, 0, -1));
    }

    @Test
    void startOrEndAboveTotalIsRejected() {
        assertNotNull(WordsReportController.validateCounts(3, 4, 0));
        assertNotNull(WordsReportController.validateCounts(3, 0, 4));
    }

    @Test
    void converterReturnsNullForNonNumbers() {
        var converter = new WordsReportController.LenientIntegerConverter();
        assertEquals(42, converter.fromString(" 42 "));
        assertNull(converter.fromString("forty"));
        assertNull(converter.fromString("1.5"));
        assertNull(converter.fromString(""));
        assertNull(converter.fromString(null));
        assertEquals("7", converter.toString(7));
    }
}
