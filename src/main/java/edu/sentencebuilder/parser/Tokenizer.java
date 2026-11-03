package edu.sentencebuilder.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns raw text into sentences of lowercase words.
 * Sentences end at {@code . ! ?}, except periods between digits or in common abbreviations. Apostrophes and
 * hyphens inside words ("don't", "state-of-the-art") are kept; other punctuation is stripped.
 * Em-dashes and en-dashes separate words, ellipses end sentences, and sentence boundaries
 * include trailing quotes and parentheses.
 */
public class Tokenizer {

    // Bypasses common abbreviations, including internal periods in i.e. and e.g. Includes closing quotes/parentheses.
    private static final String ABBREVIATIONS = "(?i)(?<!\\bmr)(?<!\\bmrs)(?<!\\bms)(?<!\\bdr)(?<!\\bvs)(?<!\\be\\.g)(?<!\\bi\\.e)(?!(?<=\\be)\\.g)(?!(?<=\\bi)\\.e)";

    // Only a single period between digits belongs to a number; ! and ? always end sentences.
    private static final Pattern SENTENCE_END = Pattern.compile(
            "[!?]+[\"'”’)]*|" +
            ABBREVIATIONS + "(?:(?<!\\p{Nd})\\.+[\"'”’)]*|\\.+(?!\\p{Nd})[\"'”’)]*)"
    );
    private static final Pattern STRIP = Pattern.compile("[^\\p{L}\\p{N}'\\s.\\-]");
    private static final Pattern NON_INTERNAL_HYPHEN = Pattern.compile(
            "(?<![\\p{L}\\p{N}])-|-(?![\\p{L}\\p{N}])");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Offset just after the last sentence end, or zero; shares the importer's decimal rules. */
    public int finishedLength(String text) {
        Matcher ends = SENTENCE_END.matcher(text);
        int length = 0;
        while (ends.find()) {
            length = ends.end();
        }
        return length;
    }

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
        cleaned = NON_INTERNAL_HYPHEN.matcher(cleaned).replaceAll(" ");
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
