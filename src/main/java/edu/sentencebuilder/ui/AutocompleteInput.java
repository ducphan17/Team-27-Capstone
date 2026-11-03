// Tien Phan

package edu.sentencebuilder.ui;

import edu.sentencebuilder.parser.Tokenizer;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

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
    // A following g or e could turn this period into the start of e.g. or i.e.
    private static final Pattern ABBREVIATION_PREFIX = Pattern.compile("(?i)\\b[ei]\\.\\z");

    private AutocompleteInput() {
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
     * True when appending text confirms a new sentence boundary. A period after a digit
     * or a single e/i waits for a following character (or Enter) to distinguish decimals
     * and abbreviations from sentence ends.
     * Edits earlier in the text never count.
     */
    static boolean endsSentence(String oldText, String newText) {
        String old = oldText == null ? "" : oldText;
        return newText != null && newText.startsWith(old)
                && finishedPart(newText).length() > finishedPart(old).length();
    }

    /**
     * The text with its last sentence ended, for Enter: trailing spaces and commas are dropped
     * and a period is added. Unchanged when the last sentence already ends with
     * {@code . ! ?} or the text has no unfinished words.
     */
    static String withEndMark(String text) {
        String trimmed = text.replaceAll("[\\s,]+$", "");
        // Enter uses completed-text boundaries, confirming periods deferred while typing.
        String lastSentence = trimmed.substring(TOKENIZER.finishedLength(trimmed));
        return TOKENIZER.tokenize(lastSentence).isEmpty() ? text : trimmed + ".";
    }

    /** Finished prefix, deferring a trailing period that could start a decimal or abbreviation. */
    static String finishedPart(String text) {
        int end = TOKENIZER.finishedLength(text);
        if (end == text.length() && end > 1 && text.charAt(end - 1) == '.'
                && (Character.isDigit(text.codePointBefore(end - 1))
                    || ABBREVIATION_PREFIX.matcher(text).find())) {
            end = TOKENIZER.finishedLength(text.substring(0, end - 1));
        }
        return text.substring(0, end);
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
