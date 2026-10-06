package edu.sentencebuilder.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns raw text into sentences of lowercase words.
 * First version: sentences end at {@code . ! ?}; other punctuation is stripped,
 * but apostrophes inside words ("don't", "it's") are kept.
 *
 * <p>TODO edge cases to handle later:
 * <ul>
 *   <li>Abbreviations: "Mr.", "Mrs.", "Dr.", "e.g." currently end a sentence.</li>
 *   <li>Hyphens and em-dashes: "well-known" becomes "well" + "known"; "word—word" likewise.</li>
 *   <li>Quotes after periods: {@code He said "hi."} — the closing quote lands in the next sentence (then gets stripped).</li>
 *   <li>Numbers: "3.14" splits into two sentences; decide whether numbers are words at all.</li>
 *   <li>Ellipses: "wait... what" — "..." currently ends a sentence; is that right?</li>
 * </ul>
 */
public class Tokenizer {

    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]+");
    /** Anything that is not a letter, digit, apostrophe, or whitespace. */
    private static final Pattern STRIP = Pattern.compile("[^\\p{L}\\p{N}'\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * @return one list of words per non-empty sentence; empty list for blank input
     */
    public List<List<String>> tokenize(String text) {
        List<List<String>> sentences = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return sentences;
        }
        // Normalize curly apostrophes so "don’t" and "don't" are the same word.
        String normalized = text.toLowerCase().replace('’', '\'');
        for (String rawSentence : SENTENCE_END.split(normalized)) {
            List<String> words = tokenizeSentence(rawSentence);
            if (!words.isEmpty()) {
                sentences.add(words);
            }
        }
        return sentences;
    }

    private List<String> tokenizeSentence(String sentence) {
        List<String> words = new ArrayList<>();
        String cleaned = STRIP.matcher(sentence).replaceAll(" ");
        for (String token : WHITESPACE.split(cleaned.strip())) {
            String word = trimApostrophes(token);
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }

    /** Removes apostrophes used as quote marks ('hello') but keeps inner ones (don't). */
    private static String trimApostrophes(String token) {
        int start = 0;
        int end = token.length();
        while (start < end && token.charAt(start) == '\'') {
            start++;
        }
        while (end > start && token.charAt(end - 1) == '\'') {
            end--;
        }
        return token.substring(start, end);
    }
}
