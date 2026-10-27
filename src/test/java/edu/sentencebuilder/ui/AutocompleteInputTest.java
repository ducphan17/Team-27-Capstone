// Tien Phan

package edu.sentencebuilder.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutocompleteInputTest {

    private static Optional<String> context(String text) {
        return AutocompleteInput.contextWord(text);
    }

    @Test
    void suggestionsBelongToTheLastWordTypedWithOrWithoutASpace() {
        assertEquals(Optional.of("puppies"), context("puppies"));
        assertEquals(Optional.of("puppies"), context("puppies "));
        assertEquals(Optional.of("chase"), context("puppies chase,"));
        assertEquals(Optional.of("pupp"), context("pupp")); // looked up; usually has no followers
    }

    @Test
    void backspacingTheSpaceKeepsTheSameWord() {
        assertEquals(context("puppies chase "), context("puppies chase"));
        assertEquals(Optional.of("chas"), context("puppies chas"));
        assertTrue(context("").isEmpty());
        assertTrue(context("  ").isEmpty());
    }

    @Test
    void noSuggestionsRightAfterASentenceEnds() {
        assertTrue(context("the cat.").isEmpty());
        assertTrue(context("the cat. ").isEmpty());
        assertTrue(context("the cat!").isEmpty());
        assertTrue(context("why not? ").isEmpty());
        assertEquals(Optional.of("i"), context("the cat. i"));
        assertEquals(Optional.of("there"), context("hi! there"));
    }

    @Test
    void wordsAreNormalizedLikeImportedText() {
        assertEquals(Optional.of("don't"), context("The Don’t "));
        assertEquals(Optional.of("hello"), context("'Hello' "));
    }

    @Test
    void sentenceEndsWhenAnEndMarkIsAddedAtTheEnd() {
        assertTrue(AutocompleteInput.endsSentence("the cat", "the cat."));
        assertTrue(AutocompleteInput.endsSentence("hi", "hi!"));
        assertTrue(AutocompleteInput.endsSentence("why", "why?"));
        assertTrue(AutocompleteInput.endsSentence("hello ", "hello ."));
        assertTrue(AutocompleteInput.endsSentence("", "Pasted text. More"));
        assertFalse(AutocompleteInput.endsSentence("the cat.", "the cat. "));
        assertFalse(AutocompleteInput.endsSentence("the cat.", "the cat"));
        assertFalse(AutocompleteInput.endsSentence("the cat ", "the. cat "));
    }

    @Test
    void enterEndsTheLastSentenceWithAPeriod() {
        assertEquals("puppies love cats.", AutocompleteInput.withEndMark("puppies love cats"));
        assertEquals("puppies love cats.", AutocompleteInput.withEndMark("puppies love cats, "));
        assertEquals("hi! there.", AutocompleteInput.withEndMark("hi! there"));
    }

    @Test
    void enterLeavesFinishedOrEmptyTextAlone() {
        assertEquals("the cat.", AutocompleteInput.withEndMark("the cat."));
        assertEquals("the cat! ", AutocompleteInput.withEndMark("the cat! "));
        assertEquals("why? ,", AutocompleteInput.withEndMark("why? ,"));
        assertEquals("", AutocompleteInput.withEndMark(""));
    }

    @Test
    void finishedPartStopsAtTheLastEndMark() {
        assertEquals("the cat. it ran!", AutocompleteInput.finishedPart("the cat. it ran! and"));
        assertEquals("", AutocompleteInput.finishedPart("no end yet"));
    }

    @Test
    void sentencesAreSplitAndNormalizedLikeImportedText() {
        assertEquals(List.of(List.of("the", "cat"), List.of("it", "ran")), AutocompleteInput.sentences("The cat! It ran"));
    }

    @Test
    void suggestionIsAddedAfterTheLastWord() {
        assertEquals("puppies chase ", AutocompleteInput.applySuggestion("puppies", "chase"));
        assertEquals("puppies chase ", AutocompleteInput.applySuggestion("puppies ", "chase"));
        assertEquals("puppies, chase ", AutocompleteInput.applySuggestion("puppies,", "chase"));
        assertEquals("chase ", AutocompleteInput.applySuggestion("", "chase"));
        assertEquals(Optional.of("chase"), context(AutocompleteInput.applySuggestion("puppies", "chase")));
    }
}
