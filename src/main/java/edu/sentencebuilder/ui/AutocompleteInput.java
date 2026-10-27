// Tien Phan

package edu.sentencebuilder.ui;

import edu.sentencebuilder.parser.Tokenizer;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Text rules for the Autocomplete tab, kept free of JavaFX so they can be unit-tested:
 * decides when an edit finishes a word (to look up suggestions), when it finishes a
 * sentence, which sentences still need saving, and how a picked suggestion is inserted.
 * Words are normalized by the same {@link Tokenizer} the importer uses, so typed and
 * imported words match ("The" and "the" are one word).
 */
final class AutocompleteInput {

    /** How many followers the tab shows. */
    static final int MAX_SUGGESTIONS = 5;

    private static final Tokenizer TOKENIZER = new Tokenizer();
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]");

    private AutocompleteInput() {
    }

    /** Space, comma, and period finish a word. */
    static boolean isTrigger(char c) {
        return c == ' ' || c == ',' || c == '.';
    }

    /**
     * Checks whether changing the field from {@code oldText} to {@code newText} finished a
     * word, and returns it (lowercase) so its followers can be shown. Only text added after
     * the last finished word counts: backspacing, editing earlier text, or typing a second
     * space returns empty. If several words arrive at once (a paste), the last one is returned.
     */
    static Optional<String> completedWord(String oldText, String newText) {
        if (newText == null || newText.isEmpty() || !isTrigger(newText.charAt(newText.length() - 1))) {
            return Optional.empty();
        }
        String confirmed = confirmedPart(oldText == null ? "" : oldText);
        if (!newText.startsWith(confirmed) || newText.length() == confirmed.length()) {
            return Optional.empty();
        }

        // The text between the previous trigger and the new one, plus the trigger: "cat " or "hi! ".
        String withoutTrigger = newText.substring(0, newText.length() - 1);
        String lastToken = newText.substring(confirmedPart(withoutTrigger).length());
        if (SENTENCE_END.matcher(lastToken).find()) {
            return Optional.empty(); // the word ended its sentence, so nothing follows it
        }
        List<List<String>> words = TOKENIZER.tokenize(lastToken);
        if (words.isEmpty()) {
            return Optional.empty(); // a second space, or punctuation with no word
        }
        return Optional.of(words.getLast().getLast());
    }

    /**
     * True when the text typed or pasted at the end of the field contains {@code . ! ?},
     * so a sentence has just ended. Edits earlier in the text never count.
     */
    static boolean endsSentence(String oldText, String newText) {
        String old = oldText == null ? "" : oldText;
        return newText != null && newText.startsWith(old)
                && SENTENCE_END.matcher(newText.substring(old.length())).find();
    }

    /** The text up to and including its last {@code . ! ?}; "" if no sentence has ended. */
    static String finishedPart(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            if (SENTENCE_END.matcher(String.valueOf(text.charAt(i))).matches()) {
                return text.substring(0, i + 1);
            }
        }
        return "";
    }

    /**
     * The sentences in {@code text} that have not been saved yet.
     *
     * @param saved the text already saved; always empty or ending at a sentence end
     * @param text  the text to save, usually {@link #finishedPart} of the field
     * @return one list of words per sentence. If the saved text was edited since, only
     *         the last sentence is returned, so earlier sentences are never counted twice.
     */
    static List<List<String>> unsavedSentences(String saved, String text) {
        if (text.startsWith(saved)) {
            return TOKENIZER.tokenize(text.substring(saved.length()));
        }
        List<List<String>> sentences = TOKENIZER.tokenize(text);
        return sentences.isEmpty() ? sentences : List.of(sentences.getLast());
    }

    /**
     * Returns the field text after picking {@code suggestion}: any partly typed word is
     * replaced, and a space follows so the word counts as finished.
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
