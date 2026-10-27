// Tien Phan

package edu.sentencebuilder.ui;

import edu.sentencebuilder.model.Word;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers which Autocomplete sentences have been handled, so each one is saved exactly
 * once even when its save fails. Kept free of JavaFX so the rules can be unit-tested.
 * Thread-safe: the controller takes batches on the JavaFX Application Thread, and the save
 * thread reports results and snapshots {@link #unconfirmed()} for the pending file.
 *
 * <p>A sentence is "handled" once it is taken for saving. If the save fails, it waits for a
 * retry. Retries go out in their own batch, so a sentence that keeps failing never blocks
 * new ones, and automatic retries stop after {@link #MAX_AUTOMATIC_RETRIES} failed attempts.
 * Sentences with an oversized word are rejected when taken instead of being retried forever.
 */
final class AutocompleteSaves {

    /** Failed retry attempts in a row after which only a manual retry (Enter) is made. */
    static final int MAX_AUTOMATIC_RETRIES = 3;

    /**
     * Sentences to save in one transaction.
     *
     * @param sentences the sentences, in typing order
     * @param rejected  why new sentences were not saved (a word is too long)
     * @param retry     true for sentences whose earlier save failed
     */
    record Batch(List<List<String>> sentences, List<String> rejected, boolean retry) {
        boolean isEmpty() {
            return sentences.isEmpty();
        }
    }

    /** Current text of one sentence occurrence and its saved or queued prefix. */
    private record HandledSentence(List<String> words, List<String> covered) {
    }

    // One entry per current occurrence, including rejected text with no saved coverage.
    private final List<HandledSentence> handled = new ArrayList<>();
    // Sentences whose save failed. Not tied to the field, so clearing it keeps them.
    private final List<List<String>> waiting = new ArrayList<>();
    // Sentences taken for a save (first attempt or retry) whose result is not known yet.
    private final List<List<String>> saving = new ArrayList<>();
    private int failedRetries;

    /** Takes the new sentences in {@code text}; waiting retries are taken separately. */
    synchronized Batch take(String text) {
        // Match the text's sentences against handled ones, one to one, so a sentence typed
        // twice is new the second time. Handled sentences left unmatched were edited away.
        List<HandledSentence> unmatched = new ArrayList<>(handled);
        List<HandledSentence> current = new ArrayList<>();
        List<List<String>> fresh = new ArrayList<>();
        for (List<String> sentence : AutocompleteInput.sentences(text)) {
            HandledSentence match = unmatched.stream()
                    .filter(earlier -> earlier.words().equals(sentence)).findFirst().orElse(null);
            if (match == null) {
                fresh.add(sentence);
            } else {
                unmatched.remove(match);
                current.add(match);
            }
        }

        List<List<String>> sentences = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (List<String> sentence : fresh) {
            HandledSentence earlier = takeRelated(sentence, unmatched);
            List<String> covered = earlier == null ? List.of() : earlier.covered();
            List<String> part = newPart(sentence, covered);
            try {
                part.forEach(Word::validateTextLength);
                if (!part.isEmpty()) {
                    sentences.add(part);
                    covered = sentence;
                }
            } catch (IllegalArgumentException e) {
                rejected.add(e.getMessage());
            }
            // Remember rejected text to suppress repeated errors, but never mark its
            // unsaved words as covered. Shortening retains the longer saved prefix.
            current.add(new HandledSentence(sentence, covered));
        }
        handled.clear();
        handled.addAll(current);
        saving.addAll(sentences);
        return new Batch(List.copyOf(sentences), List.copyOf(rejected), false);
    }

    /**
     * Matches a shortened or continued sentence to one unmatched occurrence from the previous
     * field contents. Its coverage follows that occurrence instead of becoming a historical
     * sentence that could swallow a newly typed repetition.
     */
    private static HandledSentence takeRelated(List<String> sentence, List<HandledSentence> unmatched) {
        HandledSentence match = null;
        for (HandledSentence earlier : unmatched) {
            if ((startsWith(earlier.words(), sentence) || startsWith(sentence, earlier.words()))
                    && (match == null || earlier.words().size() > match.words().size())) {
                match = earlier;
            }
        }
        unmatched.remove(match); // each occurrence can be continued or shortened only once
        return match;
    }

    /** Returns only the words and pairs beyond this occurrence's saved or queued prefix. */
    private static List<String> newPart(List<String> sentence, List<String> covered) {
        if (covered.isEmpty()) {
            return sentence;
        }
        if (startsWith(covered, sentence)) {
            return List.of();
        }
        return startsWith(sentence, covered)
                ? List.copyOf(sentence.subList(covered.size() - 1, sentence.size())) : sentence;
    }

    private static boolean startsWith(List<String> sentence, List<String> prefix) {
        return sentence.size() >= prefix.size() && sentence.subList(0, prefix.size()).equals(prefix);
    }

    /** Takes every waiting sentence for one retry attempt. */
    synchronized Batch takeWaiting() {
        Batch batch = new Batch(List.copyOf(waiting), List.of(), true);
        saving.addAll(waiting);
        waiting.clear();
        return batch;
    }

    /** The batch's save failed: its sentences wait for the next attempt. */
    synchronized void failed(Batch batch) {
        removeEach(saving, batch.sentences());
        waiting.addAll(batch.sentences());
        if (batch.retry()) {
            failedRetries++;
        }
    }

    /** The batch's save committed. */
    synchronized void succeeded(Batch batch) {
        removeEach(saving, batch.sentences());
        if (batch.retry()) {
            failedRetries = 0;
        }
    }

    /** True when sentences are waiting and automatic retries have not given up yet. */
    synchronized boolean retriesAutomatically() {
        return !waiting.isEmpty() && failedRetries < MAX_AUTOMATIC_RETRIES;
    }

    synchronized int waitingCount() {
        return waiting.size();
    }

    /** Sentences not confirmed as saved: being saved or waiting. These must survive a restart. */
    synchronized List<List<String>> unconfirmed() {
        List<List<String>> all = new ArrayList<>(saving);
        all.addAll(waiting);
        return all;
    }

    /** Adds sentences left unsaved by an earlier run of the app; they wait for a retry. */
    synchronized void restore(List<List<String>> sentences) {
        waiting.addAll(sentences);
    }

    /** The field was emptied: anything typed from now on is new. Waiting retries are kept. */
    synchronized void fieldCleared() {
        handled.clear();
    }

    // Removes one copy of each sentence, so repeated sentences are matched one to one.
    private static void removeEach(List<List<String>> from, List<List<String>> sentences) {
        sentences.forEach(from::remove);
    }
}
