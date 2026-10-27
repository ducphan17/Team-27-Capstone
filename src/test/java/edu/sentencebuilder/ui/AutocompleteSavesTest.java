// Tien Phan

package edu.sentencebuilder.ui;

import edu.sentencebuilder.ui.AutocompleteSaves.Batch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutocompleteSavesTest {

    private static final List<String> THE_CAT = List.of("the", "cat");
    private static final List<String> IT_RAN = List.of("it", "ran");

    private final AutocompleteSaves saves = new AutocompleteSaves();

    @Test
    void typingADecimalThenEnterSavesOnlyTheCompleteNumber() {
        String text = "Version 1.8";
        for (int length = 1; length <= text.length(); length++) {
            String oldText = text.substring(0, length - 1);
            String newText = text.substring(0, length);
            if (AutocompleteInput.endsSentence(oldText, newText)) {
                saves.take(AutocompleteInput.finishedPart(newText));
            }
        }
        assertTrue(saves.unconfirmed().isEmpty(), "No integer prefix should be queued while typing");
        assertEquals(List.of(List.of("version", "1.8")),
                saves.take(AutocompleteInput.withEndMark(text)).sentences());
        assertTrue(saves.take(AutocompleteInput.withEndMark(text)).isEmpty());
    }

    @Test
    void deletingTheEndMarkThenSavingAgainSavesNothing() {
        for (String mark : List.of(".", "!", "?")) {
            AutocompleteSaves fresh = new AutocompleteSaves();
            assertEquals(List.of(THE_CAT), fresh.take("the cat" + mark).sentences());
            assertTrue(fresh.take("the cat").isEmpty(), "after deleting " + mark);
        }
    }

    @Test
    void changingOrRetypingTheEndMarkSavesNothingAgain() {
        saves.take("the cat.");
        assertTrue(saves.take("the cat!").isEmpty());
        assertTrue(saves.take("the cat?").isEmpty());
        assertTrue(saves.take("the cat.").isEmpty());
    }

    @Test
    void enterAddsAPeriodThatSavesNothingAgain() {
        // Enter turns "the cat" into "the cat."; continuing afterwards starts a new sentence.
        assertEquals(List.of(THE_CAT), saves.take(AutocompleteInput.withEndMark("the cat")).sentences());
        assertEquals(List.of(IT_RAN), saves.take("the cat. it ran.").sentences());
    }

    @Test
    void editingAnEarlierSentenceDoesNotResaveTheOthers() {
        saves.take("the cat. it ran.");
        assertEquals(List.of(List.of("the", "dog")), saves.take("the dog. it ran!").sentences());
    }

    @Test
    void sameSentenceTypedAgainIsNew() {
        saves.take("the cat.");
        assertEquals(List.of(THE_CAT), saves.take("the cat. the cat.").sentences());
    }

    @Test
    void earlierVersionTypedAsANewSentenceIsSavedAgain() {
        saves.succeeded(saves.take("the cat."));
        saves.succeeded(saves.take("the cat sat."));
        assertEquals(List.of(THE_CAT), saves.take("the cat sat. the cat.").sentences());
    }

    @Test
    void shorteningARejectedSentenceSavesItsValidWords() {
        Batch rejected = saves.take("the cat " + "a".repeat(101) + ".");
        assertTrue(rejected.isEmpty());
        assertEquals(1, rejected.rejected().size());
        assertEquals(List.of(THE_CAT), saves.take("the cat.").sentences());
    }

    @Test
    void correctingARejectedContinuationKeepsOnlyItsPreviouslySavedPrefix() {
        saves.succeeded(saves.take("the cat."));
        Batch rejected = saves.take("the cat sat " + "a".repeat(101) + ".");
        assertTrue(rejected.isEmpty());
        assertEquals(1, rejected.rejected().size());
        assertEquals(List.of(List.of("cat", "sat")), saves.take("the cat sat.").sentences());
        assertTrue(saves.take("the cat sat.").isEmpty());
    }

    @Test
    void shorteningThenContinuingRetainsTheLongestSavedPrefix() {
        saves.succeeded(saves.take("the cat sat."));
        assertTrue(saves.take("the cat.").isEmpty());
        assertTrue(saves.take("the cat sat.").isEmpty());
        assertEquals(List.of(List.of("sat", "down")), saves.take("the cat sat down.").sentences());
    }

    @Test
    void shorteningOneOccurrenceDoesNotCoverAnotherNewSentence() {
        saves.succeeded(saves.take("the cat sat."));
        assertEquals(List.of(THE_CAT), saves.take("the cat. the cat.").sentences());
        assertTrue(saves.take("the cat. the cat.").isEmpty());
    }

    @Test
    void continuingASavedSentenceSavesOnlyTheNewPairs() {
        saves.take(AutocompleteInput.withEndMark("puppies love cats")); // Enter
        // Period deleted and more typed: only cats -> and -> dogs is new.
        assertEquals(List.of(List.of("cats", "and", "dogs")), saves.take("puppies love cats and dogs.").sentences());
        assertEquals(List.of(List.of("dogs", "too")), saves.take("puppies love cats and dogs too.").sentences());
        // Back to an earlier version: everything in it is saved already.
        assertTrue(saves.take("puppies love cats.").isEmpty());
    }

    @Test
    void shorteningASavedSentenceSavesNothing() {
        saves.take("puppies love cats.");
        assertTrue(saves.take("puppies love.").isEmpty());
    }

    @Test
    void sentenceBeingSavedIsNotTakenAgain() {
        saves.take("the cat.");
        // No success or failure reported yet: the save is still running.
        assertTrue(saves.take("the cat. ").isEmpty());
    }

    @Test
    void failedSentencesAreRetriedInTheirOwnBatch() {
        saves.failed(saves.take("the cat."));
        assertEquals(1, saves.waitingCount());

        // New sentences never wait behind a failing retry.
        Batch next = saves.take("the cat. it ran.");
        assertEquals(List.of(IT_RAN), next.sentences());
        assertFalse(next.retry());

        Batch retry = saves.takeWaiting();
        assertEquals(List.of(THE_CAT), retry.sentences());
        assertTrue(retry.retry());
        assertTrue(saves.takeWaiting().isEmpty(), "A retry in progress is not sent twice");
    }

    @Test
    void automaticRetriesStopAfterTheLimitAndResumeAfterASuccess() {
        saves.failed(saves.take("the cat."));
        for (int attempt = 1; attempt <= AutocompleteSaves.MAX_AUTOMATIC_RETRIES; attempt++) {
            assertTrue(saves.retriesAutomatically(), "before failed retry " + attempt);
            saves.failed(saves.takeWaiting());
        }
        assertFalse(saves.retriesAutomatically());
        assertEquals(1, saves.waitingCount(), "Still waiting for a manual retry");

        Batch manual = saves.takeWaiting();
        saves.succeeded(manual);
        assertEquals(0, saves.waitingCount());

        saves.failed(saves.take("the cat. it ran."));
        assertTrue(saves.retriesAutomatically(), "A success resets the limit");
    }

    @Test
    void sentencesStayUnconfirmedFromTakingUntilASaveSucceeds() {
        Batch first = saves.take("the cat.");
        assertEquals(List.of(THE_CAT), saves.unconfirmed(), "Unconfirmed during the first attempt");

        saves.failed(first);
        assertEquals(List.of(THE_CAT), saves.unconfirmed(), "Unconfirmed while waiting");

        Batch retry = saves.takeWaiting();
        assertEquals(List.of(THE_CAT), saves.unconfirmed(), "Unconfirmed while retrying");

        saves.succeeded(retry);
        assertEquals(List.of(), saves.unconfirmed());
    }

    @Test
    void failedSentencesSurviveClearingTheField() {
        saves.failed(saves.take("the cat."));
        saves.fieldCleared();
        assertEquals(List.of(THE_CAT), saves.takeWaiting().sentences());
    }

    @Test
    void restoredSentencesWaitForARetry() {
        saves.restore(List.of(THE_CAT, IT_RAN));
        assertEquals(List.of(THE_CAT, IT_RAN), saves.takeWaiting().sentences());
    }

    @Test
    void afterClearingTheFieldTheSameSentenceIsNewAgain() {
        saves.take("the cat.");
        saves.fieldCleared();
        assertEquals(List.of(THE_CAT), saves.take("the cat.").sentences());
    }

    @Test
    void oversizedWordIsRejectedOnceAndDoesNotBlockOtherSentences() {
        String text = "the cat. " + "a".repeat(101) + ".";
        Batch batch = saves.take(text);
        assertEquals(List.of(THE_CAT), batch.sentences());
        assertEquals(1, batch.rejected().size());
        assertTrue(batch.rejected().getFirst().contains("100-character limit"));

        Batch again = saves.take(text);
        assertTrue(again.isEmpty());
        assertTrue(again.rejected().isEmpty(), "The rejection is reported only once");
    }
}
