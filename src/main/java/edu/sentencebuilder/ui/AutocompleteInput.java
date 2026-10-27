// Tien Phan

package edu.sentencebuilder.ui;

import edu.sentencebuilder.parser.Tokenizer;

import java.util.List;
import java.util.Optional;

/**
 * Text rules for the Autocomplete tab, kept free of JavaFX so they can be unit-tested:
 * which word the suggestions belong to, when an edit ends a sentence, which sentences are
 * new, how Enter ends a sentence, and how a picked suggestion is inserted. Words are normalized by the same
 * {@link Tokenizer} the importer uses, so typed and imported words match ("The" and "the"
 * are one word).
 */
final class AutocompleteInput {

    /** How many followers the tab shows. */
    static final int MAX_SUGGESTIONS = 5;

    private static final Tokenizer TOKENIZER = new Tokenizer();

    private AutocompleteInput() {
    }

    /** {@code . ! ?} end a sentence, as in the importer's {@link Tokenizer}. */
    private static boolean isSentenceEnd(char c) {
        return c == '.' || c == '!' || c == '?';
    }

    /**
     * The word whose followers should be shown: the last word of the current sentence, whether
     * or not a space follows it, so suggestions appear as soon as a known word is typed and
     * deleting the space after it changes nothing. Empty when the sentence has no words yet or
     * has just ended, since imported pairs never cross a sentence end.
     */
    static Optional<String> contextWord(String text) {
        String current = text == null ? "" : text;
        List<List<String>> words = TOKENIZER.tokenize(current.substring(finishedPart(current).length()));
        return words.isEmpty() ? Optional.empty() : Optional.of(words.getLast().getLast());
    }

    /**
     * True when the text typed or pasted at the end of the field contains {@code . ! ?},
     * so a sentence has just ended. Edits earlier in the text never count.
     */
    static boolean endsSentence(String oldText, String newText) {
        String old = oldText == null ? "" : oldText;
        return newText != null && newText.startsWith(old)
                && newText.substring(old.length()).chars().anyMatch(c -> isSentenceEnd((char) c));
    }

    /**
     * The text with its last sentence ended, for Enter: trailing spaces and commas are dropped
     * and a period is added. Unchanged when the last sentence already ends with
     * {@code . ! ?} or the text has no unfinished words.
     */
    static String withEndMark(String text) {
        String trimmed = text.replaceAll("[\\s,]+$", "");
        String lastSentence = trimmed.substring(finishedPart(trimmed).length());
        return TOKENIZER.tokenize(lastSentence).isEmpty() ? text : trimmed + ".";
    }

    /** The text up to and including its last {@code . ! ?}; "" if no sentence has ended. */
    static String finishedPart(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            if (isSentenceEnd(text.charAt(i))) {
                return text.substring(0, i + 1);
            }
        }
        return "";
    }

    /** The sentences in {@code text}, as lowercase words, split like imported text. */
    static List<List<String>> sentences(String text) {
        return TOKENIZER.tokenize(text);
    }

    /**
     * Returns the field text after picking {@code suggestion}, a follower of the last word:
     * the suggestion is added after that word, followed by a space.
     */
    static String applySuggestion(String text, String suggestion) {
        String current = text == null ? "" : text;
        boolean needsSpace = !current.isEmpty() && !Character.isWhitespace(current.charAt(current.length() - 1));
        return current + (needsSpace ? " " : "") + suggestion + " ";
    }
}
