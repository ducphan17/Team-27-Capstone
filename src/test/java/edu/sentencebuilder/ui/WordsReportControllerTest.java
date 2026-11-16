package edu.sentencebuilder.ui;

import edu.sentencebuilder.model.Word;
import edu.sentencebuilder.generator.Follower;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordsReportControllerTest {

    @Test
    void inspectorProbabilitiesIncludeBothImportedAndUserCounts() {
        var rows = WordsReportController.followerDetails(List.of(
                new Follower("cat", 12, 3), new Follower("dog", 8, 0), new Follower("bird", 2, 0)));
        assertEquals("cat", rows.get(0).text());
        assertEquals(12, rows.get(0).importedCount());
        assertEquals(3, rows.get(0).userCount());
        assertEquals(15L, rows.get(0).weight());
        assertEquals(0.60, rows.get(0).probability(), 1e-12);
        assertEquals(0.32, rows.get(1).probability(), 1e-12);
        assertEquals(0.08, rows.get(2).probability(), 1e-12);
    }

    @Test
    void inspectorHandlesEmptyAndZeroWeightFollowers() {
        assertTrue(WordsReportController.followerDetails(List.of()).isEmpty());
        var rows = WordsReportController.followerDetails(List.of(new Follower("cat", 0, 0)));
        assertEquals(0.0, rows.getFirst().probability());
    }

    @Test
    void inspectorDoesNotOverflowWhenCombiningLargeCounts() {
        var rows = WordsReportController.followerDetails(List.of(
                new Follower("cat", Integer.MAX_VALUE, Integer.MAX_VALUE),
                new Follower("dog", Integer.MAX_VALUE, Integer.MAX_VALUE)));
        assertEquals(4294967294L, rows.getFirst().weight());
        assertEquals(0.5, rows.getFirst().probability());
    }

    @Test
    void inspectorClampsNegativeCombinedWeights() {
        var rows = WordsReportController.followerDetails(List.of(
                new Follower("invalid", -10, 1), new Follower("valid", 2, 0)));
        assertEquals(0L, rows.get(0).weight());
        assertEquals(0.0, rows.get(0).probability());
        assertEquals(1.0, rows.get(1).probability());
    }

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
