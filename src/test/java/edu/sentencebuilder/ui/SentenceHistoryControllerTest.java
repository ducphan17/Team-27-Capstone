package edu.sentencebuilder.ui;

import edu.sentencebuilder.model.GeneratedSentence;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentenceHistoryControllerTest {

    private static GeneratedSentence sentence(String text) {
        return new GeneratedSentence(text, "the", "Weighted random");
    }

    @Test
    void emptyHistoryHasNoCounts() {
        assertTrue(SentenceHistoryController.countOccurrences(List.of()).isEmpty());
        assertEquals("0 sentences", SentenceHistoryController.summary(List.of(), Map.of()));
    }

    @Test
    void countsRepeatedSentences() {
        List<GeneratedSentence> history = List.of(
                sentence("The cat sat."), sentence("The dog ran."), sentence("The cat sat."), sentence("The cat sat."));
        Map<String, Integer> counts = SentenceHistoryController.countOccurrences(history);
        assertEquals(3, counts.get("The cat sat."));
        assertEquals(1, counts.get("The dog ran."));
    }

    @Test
    void duplicateMatchingIsExact() {
        Map<String, Integer> counts = SentenceHistoryController.countOccurrences(
                List.of(sentence("The cat sat."), sentence("The cat sat")));
        assertEquals(1, counts.get("The cat sat."));
        assertEquals(1, counts.get("The cat sat"));
    }

    @Test
    void summaryMentionsDuplicatesOnlyWhenPresent() {
        List<GeneratedSentence> unique = List.of(sentence("A."));
        assertEquals("1 sentence",
                SentenceHistoryController.summary(unique, SentenceHistoryController.countOccurrences(unique)));

        List<GeneratedSentence> repeated = List.of(sentence("A."), sentence("A."), sentence("B."));
        assertEquals("3 sentences, 1 generated more than once (highlighted)",
                SentenceHistoryController.summary(repeated, SentenceHistoryController.countOccurrences(repeated)));
    }
}
