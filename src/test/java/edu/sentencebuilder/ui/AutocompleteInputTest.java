// Tien Phan

package edu.sentencebuilder.ui;

import edu.sentencebuilder.ui.AutocompleteInput.Completion;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutocompleteInputTest {

    private static Optional<Completion> type(String oldText, String newText) {
        return AutocompleteInput.completedWord(oldText, newText);
    }

    @Test
    void spaceFinishesTheFirstWordWithNoPreviousWord() {
        assertEquals(Optional.of(new Completion(null, "the", false)), type("the", "the "));
    }

    @Test
    void spaceFinishesAWordAfterThePreviousOne() {
        assertEquals(Optional.of(new Completion("the", "cat", false)), type("the cat", "the cat "));
    }

    @Test
    void commaFinishesAWordAndKeepsThePairAcrossIt() {
        assertEquals(Optional.of(new Completion("the", "cat", false)), type("the cat", "the cat,"));
        // The importer strips commas, so "cat, sat" is still a pair.
        assertEquals(Optional.of(new Completion("cat", "sat", false)), type("the cat, sat", "the cat, sat "));
    }

    @Test
    void periodEndsTheSentenceAndTheNextWordStartsFresh() {
        assertEquals(Optional.of(new Completion("the", "cat", true)), type("the cat", "the cat."));
        assertEquals(Optional.of(new Completion(null, "it", false)), type("the cat. it", "the cat. it "));
    }

    @Test
    void exclamationAndQuestionMarksAlsoEndTheSentence() {
        assertEquals(Optional.of(new Completion(null, "hi", true)), type("hi!", "hi! "));
        assertEquals(Optional.of(new Completion("why", "not", true)), type("why not?", "why not? "));
    }

    @Test
    void secondTriggerAfterAFinishedWordRecordsNothing() {
        assertTrue(type("the ", "the  ").isEmpty());
        assertTrue(type("the,", "the, ").isEmpty());
        assertTrue(type("the.", "the. ").isEmpty());
        assertTrue(type("", " ").isEmpty());
    }

    @Test
    void backspacingOrEditingEarlierTextRecordsNothing() {
        assertTrue(type("the cat s", "the cat ").isEmpty());
        assertTrue(type("the cat, ", "the cat,").isEmpty());
        assertTrue(type("the cat ", "the bat ").isEmpty());
    }

    @Test
    void typingInsideAWordRecordsNothing() {
        assertTrue(type("the ca", "the cat").isEmpty());
        assertTrue(type("", "t").isEmpty());
        assertTrue(type("the", "").isEmpty());
    }

    @Test
    void wordsAreNormalizedLikeImportedText() {
        assertEquals(Optional.of(new Completion("the", "don't", false)), type("The Don’t", "The Don’t "));
        assertEquals(Optional.of(new Completion(null, "hello", false)), type("'Hello'", "'Hello' "));
    }

    @Test
    void pastingSeveralWordsRecordsOnlyTheLast() {
        assertEquals(Optional.of(new Completion("cat", "sat", false)), type("", "the cat sat "));
    }

    @Test
    void suggestionReplacesAPartlyTypedWordAndAddsASpace() {
        assertEquals("the cat ", AutocompleteInput.applySuggestion("the ", "cat"));
        assertEquals("the cat ", AutocompleteInput.applySuggestion("the ca", "cat"));
        assertEquals("the, cat ", AutocompleteInput.applySuggestion("the,", "cat"));
        assertEquals("cat ", AutocompleteInput.applySuggestion("", "cat"));
    }

    @Test
    void pickedSuggestionIsSeenAsOneFinishedWord() {
        String before = "the xy";
        String after = AutocompleteInput.applySuggestion(before, "cat");
        assertEquals(Optional.of(new Completion("the", "cat", false)), type(before, after));
    }
}
