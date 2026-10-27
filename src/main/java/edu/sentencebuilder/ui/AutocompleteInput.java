// Tien Phan

package edu.sentencebuilder.ui;

import edu.sentencebuilder.parser.Tokenizer;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Text rules for the Autocomplete tab, kept free of JavaFX so they can be unit-tested:
 * decides when an edit finishes a word, which word comes before it, and how a picked
 * suggestion is inserted. Words are normalized by the same {@link Tokenizer} the importer
 * uses, so typed and imported words match ("The" and "the" are one word).
 */
final class AutocompleteInput {

    /** How many followers the tab shows. */
    static final int MAX_SUGGESTIONS = 5;

    private static final Tokenizer TOKENIZER = new Tokenizer();
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]");

    /**
     * A word the user just finished.
     *
     * @param previousWord  the word before it in the same sentence, or {@code null} at a sentence start
     * @param word          the finished word, lowercase
     * @param sentenceEnded true when the word closed its sentence, so nothing follows it
     */
    record Completion(String previousWord, String word, boolean sentenceEnded) {
    }

    private AutocompleteInput() {
    }

    /** Space, comma, and period finish a word. */
    static boolean isTrigger(char c) {
        return c == ' ' || c == ',' || c == '.';
    }

    /**
     * Checks whether changing the field from {@code oldText} to {@code newText} finished a word.
     * Only text added after the last finished word counts: backspacing, editing earlier text,
     * or typing a second space returns empty. If several words arrive at once (a paste), only
     * the last one is returned.
     */
    static Optional<Completion> completedWord(String oldText, String newText) {
        if (newText == null || newText.isEmpty() || !isTrigger(newText.charAt(newText.length() - 1))) {
            return Optional.empty();
        }
        String confirmed = confirmedPart(oldText == null ? "" : oldText);
        if (!newText.startsWith(confirmed) || newText.length() == confirmed.length()) {
            return Optional.empty();
        }

        // The text between the previous trigger and the new one, e.g. "cat" or "hi!".
        String withoutTrigger = newText.substring(0, newText.length() - 1);
        String lastToken = withoutTrigger.substring(confirmedPart(withoutTrigger).length());
        if (TOKENIZER.tokenize(lastToken).isEmpty()) {
            return Optional.empty(); // a second space, or punctuation with no word
        }

        // Tokenize everything so the previous word follows the importer's rules: commas
        // don't separate pairs, and . ! ? start a new sentence.
        List<List<String>> sentences = TOKENIZER.tokenize(newText);
        List<String> sentence = sentences.getLast();
        String word = sentence.getLast();
        String previous = sentence.size() > 1 ? sentence.get(sentence.size() - 2) : null;
        boolean ended = SENTENCE_END.matcher(lastToken + newText.charAt(newText.length() - 1)).find();
        return Optional.of(new Completion(previous, word, ended));
    }

    /**
     * Returns the field text after picking {@code suggestion}: any partly typed word is
     * replaced, and a space follows so the listener sees the word as finished.
     */
    static String applySuggestion(String text, String suggestion) {
        String prefix = confirmedPart(text == null ? "" : text);
        if (!prefix.isEmpty() && prefix.charAt(prefix.length() - 1) != ' ') {
            prefix += " ";
        }
        return prefix + suggestion + " ";
    }

    /** The text up to and including its last trigger character; "" if there is none. */
    private static String confirmedPart(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            if (isTrigger(text.charAt(i))) {
                return text.substring(0, i + 1);
            }
        }
        return "";
    }
}
