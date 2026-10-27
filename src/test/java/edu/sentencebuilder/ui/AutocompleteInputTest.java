// Tien Phan

package edu.sentencebuilder.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutocompleteInputTest {

    private static Optional<String> type(String oldText, String newText) {
        return AutocompleteInput.completedWord(oldText, newText);
    }

    @Test
    void spaceOrCommaFinishesAWord() {
        assertEquals(Optional.of("the"), type("the", "the "));
        assertEquals(Optional.of("cat"), type("the cat", "the cat,"));
        assertEquals(Optional.of("sat"), type("the cat, sat", "the cat, sat "));
    }

    @Test
    void wordThatEndsASentenceGetsNoSuggestions() {
        assertTrue(type("the cat", "the cat.").isEmpty());
        assertTrue(type("hi!", "hi! ").isEmpty());
        assertEquals(Optional.of("it"), type("the cat. it", "the cat. it "));
    }

    @Test
    void secondTriggerAfterAFinishedWordFinishesNothing() {
        assertTrue(type("the ", "the  ").isEmpty());
        assertTrue(type("the,", "the, ").isEmpty());
        assertTrue(type("the.", "the. ").isEmpty());
        assertTrue(type("", " ").isEmpty());
    }

    @Test
    void backspacingEditingOrTypingInsideAWordFinishesNothing() {
        assertTrue(type("the cat s", "the cat ").isEmpty());
        assertTrue(type("the cat, ", "the cat,").isEmpty());
        assertTrue(type("the cat ", "the bat ").isEmpty());
        assertTrue(type("the ca", "the cat").isEmpty());
        assertTrue(type("the", "").isEmpty());
    }

    @Test
    void wordsAreNormalizedLikeImportedText() {
        assertEquals(Optional.of("don't"), type("The Don’t", "The Don’t "));
        assertEquals(Optional.of("hello"), type("'Hello'", "'Hello' "));
    }

    @Test
    void sentenceEndsWhenAnEndMarkIsAddedAtTheEnd() {
        assertTrue(AutocompleteInput.endsSentence("the cat", "the cat."));
        assertTrue(AutocompleteInput.endsSentence("hi", "hi!"));
        assertTrue(AutocompleteInput.endsSentence("hello ", "hello ."));
        assertTrue(AutocompleteInput.endsSentence("", "Pasted text. More"));
        assertFalse(AutocompleteInput.endsSentence("the cat.", "the cat. "));
        assertFalse(AutocompleteInput.endsSentence("the cat.", "the cat"));
        assertFalse(AutocompleteInput.endsSentence("the cat ", "the. cat "));
    }

    @Test
    void finishedPartStopsAtTheLastEndMark() {
        assertEquals("the cat. it ran!", AutocompleteInput.finishedPart("the cat. it ran! and"));
        assertEquals("", AutocompleteInput.finishedPart("no end yet"));
    }

    @Test
    void correctedTypoIsNotSaved() {
        // The field only holds the corrected text when the sentence ends.
        assertEquals(List.of(List.of("the", "cat")), AutocompleteInput.unsavedSentences("", "The cat."));
    }

    @Test
    void savedSentencesAreNotSavedAgain() {
        assertEquals(List.of(List.of("it", "ran")),
                AutocompleteInput.unsavedSentences("the cat.", "the cat. It ran."));
        // Backspacing over a period and typing it again saves nothing new.
        assertEquals(List.of(), AutocompleteInput.unsavedSentences("the cat.", "the cat."));
    }

    @Test
    void editedSavedTextSavesOnlyTheLastSentence() {
        assertEquals(List.of(List.of("it", "ran")),
                AutocompleteInput.unsavedSentences("the cat.", "the dog. It ran."));
    }

    @Test
    void enterSavesAnUnfinishedSentence() {
        assertEquals(List.of(List.of("it", "ran")),
                AutocompleteInput.unsavedSentences("the cat.", "the cat. it ran"));
    }

    @Test
    void suggestionReplacesAPartlyTypedWordAndAddsASpace() {
        assertEquals("the cat ", AutocompleteInput.applySuggestion("the ", "cat"));
        assertEquals("the cat ", AutocompleteInput.applySuggestion("the ca", "cat"));
        assertEquals("the, cat ", AutocompleteInput.applySuggestion("the,", "cat"));
        assertEquals("cat ", AutocompleteInput.applySuggestion("", "cat"));
        assertEquals(Optional.of("cat"), type("the xy", AutocompleteInput.applySuggestion("the xy", "cat")));
    }
}
