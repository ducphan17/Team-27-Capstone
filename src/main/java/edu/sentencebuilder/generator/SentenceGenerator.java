package edu.sentencebuilder.generator;

import edu.sentencebuilder.model.Word;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Base class for sentence generators (Template Method pattern).
 * {@link #generate(String, int)} owns the loop and the stopping rules;
 * subclasses only decide which follower comes next in {@link #chooseNext}.
 * Generators never touch the database; they read stats through {@link WordStatsSource}.
 */
public abstract class SentenceGenerator {

    public static final int DEFAULT_MAX_WORDS = 20;

    protected final WordStatsSource source;
    protected final Random random;

    protected SentenceGenerator(WordStatsSource source) {
        this(source, new Random());
    }

    /** Pass a seeded {@link Random} to make output reproducible in tests. */
    protected SentenceGenerator(WordStatsSource source, Random random) {
        this.source = Objects.requireNonNull(source);
        this.random = Objects.requireNonNull(random);
    }

    /** Display name, also stored in {@code generated_sentence.algorithm}. */
    public abstract String getName();

    public String generate(String startWord) {
        return generate(startWord, DEFAULT_MAX_WORDS);
    }

    /**
     * Builds a sentence starting at {@code startWord}. Stops when the current word
     * randomly "ends" (probability end_count / total_count), has no followers,
     * or {@code maxWords} words have been produced.
     *
     * @return the sentence, capitalized and ending with a period
     */
    public String generate(String startWord, int maxWords) {
        if (startWord == null || startWord.isBlank()) {
            throw new IllegalArgumentException("startWord must not be blank");
        }
        List<String> words = new ArrayList<>();
        String current = startWord.trim().toLowerCase();
        words.add(current);

        while (words.size() < maxWords) {
            Optional<Word> stats = source.getWord(current);
            if (stats.isPresent() && shouldEndAfter(stats.get())) {
                break;
            }
            List<Follower> followers = source.getFollowers(current);
            if (followers.isEmpty()) {
                break;
            }
            String next = chooseNext(current, followers, words);
            if (next == null) {
                break;
            }
            words.add(next);
            current = next;
        }
        return formatSentence(words);
    }

    /**
     * Picks the word to follow {@code current}.
     *
     * @param current      the last word in the sentence
     * @param followers    non-empty list of candidate next words
     * @param sentenceSoFar words chosen so far (read-only use), for loop avoidance
     * @return the next word, or {@code null} to end the sentence
     */
    protected abstract String chooseNext(String current, List<Follower> followers, List<String> sentenceSoFar);

    /** True with probability end_count / total_count. */
    protected boolean shouldEndAfter(Word word) {
        if (word.getTotalCount() <= 0) {
            return false;
        }
        double endProbability = (double) word.getEndCount() / word.getTotalCount();
        return random.nextDouble() < endProbability;
    }

    private static String formatSentence(List<String> words) {
        String joined = String.join(" ", words);
        return Character.toUpperCase(joined.charAt(0)) + joined.substring(1) + ".";
    }
}
