package edu.sentencebuilder.generator;

import edu.sentencebuilder.model.Word;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests WeightedRandomGenerator against an in-memory WordStatsSource; no database needed. */
class WeightedRandomGeneratorTest {

    /** In-memory stand-in for WordPairDAO. */
    static class FakeStats implements WordStatsSource {
        final Map<String, Word> words = new HashMap<>();
        final Map<String, List<Follower>> followers = new HashMap<>();

        FakeStats word(String text, int total, int ends) {
            words.put(text, new Word(words.size() + 1, text, total, 0, ends, null));
            return this;
        }

        FakeStats pair(String from, String to, int count, int userCount) {
            followers.computeIfAbsent(from, k -> new ArrayList<>()).add(new Follower(to, count, userCount));
            return this;
        }

        @Override
        public List<Follower> getFollowers(String word) {
            return followers.getOrDefault(word, List.of());
        }

        @Override
        public Optional<Word> getWord(String text) {
            return Optional.ofNullable(words.get(text));
        }
    }

    @Test
    void followsOnlyPathAndStopsWhenNoFollowers() {
        FakeStats stats = new FakeStats()
                .word("the", 5, 0).word("cat", 5, 0).word("sat", 5, 0)
                .pair("the", "cat", 3, 0)
                .pair("cat", "sat", 2, 0);
        WeightedRandomGenerator gen = new WeightedRandomGenerator(stats, new Random(1));

        assertEquals("The cat sat.", gen.generate("the"));
    }

    @Test
    void stopsAtMaxWords() {
        FakeStats stats = new FakeStats().word("again", 10, 0).pair("again", "again", 10, 0);
        WeightedRandomGenerator gen = new WeightedRandomGenerator(stats, new Random(1));

        assertEquals(5, wordCount(gen.generate("again", 5)));
        assertEquals(SentenceGenerator.DEFAULT_MAX_WORDS, wordCount(gen.generate("again")));
    }

    @Test
    void stopsWhenWordAlwaysEndsSentences() {
        // end_count == total_count, so the end draw always succeeds.
        FakeStats stats = new FakeStats().word("stop", 4, 4).pair("stop", "never", 100, 0);
        WeightedRandomGenerator gen = new WeightedRandomGenerator(stats, new Random(1));

        assertEquals("Stop.", gen.generate("stop"));
    }

    @Test
    void unknownStartWordReturnsJustThatWord() {
        WeightedRandomGenerator gen = new WeightedRandomGenerator(new FakeStats(), new Random(1));
        assertEquals("Xyzzy.", gen.generate("Xyzzy"));
    }

    @Test
    void zeroWeightFollowerIsNeverChosen() {
        List<Follower> followers = List.of(new Follower("never", 0, 0), new Follower("always", 1, 0));
        WeightedRandomGenerator gen = new WeightedRandomGenerator(new FakeStats(), new Random(42));

        for (int i = 0; i < 1_000; i++) {
            assertEquals("always", gen.chooseNext("x", followers, List.of("x")));
        }
    }

    @Test
    void choosesProportionallyToCountPlusUserCount() {
        // a: 2 + 1 = 3, b: 1 + 0 = 1  ->  a should be picked ~75% of the time.
        List<Follower> followers = List.of(new Follower("a", 2, 1), new Follower("b", 1, 0));
        WeightedRandomGenerator gen = new WeightedRandomGenerator(new FakeStats(), new Random(42));

        int trials = 20_000;
        int picksOfA = 0;
        for (int i = 0; i < trials; i++) {
            if (gen.chooseNext("x", followers, List.of("x")).equals("a")) {
                picksOfA++;
            }
        }
        double share = (double) picksOfA / trials;
        assertTrue(Math.abs(share - 0.75) < 0.02, "expected ~0.75 but was " + share);
    }

    private static int wordCount(String sentence) {
        return sentence.split(" ").length;
    }
}
