package edu.sentencebuilder.generator;

import edu.sentencebuilder.model.Word;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MostFrequentGeneratorTest {

    @Test
    public void testLoopAvoidanceBreaksInfiniteBigram() {
        // 1. Create a fake in-memory WordStatsSource
        WordStatsSource fakeSource = new WordStatsSource() {
            @Override
            public List<Follower> getFollowers(String word) {
                // Create an artificial loop: "hello" -> "world", "world" -> "hello"
                if (word.equals("hello")) {
                    return List.of(new Follower("world", 100, 0));
                } else if (word.equals("world")) {
                    return List.of(new Follower("hello", 100, 0));
                }
                return List.of();
            }

            @Override
            public Optional<Word> getWord(String text) {
                // Returning empty ensures the random "natural end" stopping rule never triggers.
                // This forces the generator to rely entirely on your loop-avoidance logic.
                return Optional.empty();
            }
        };

        MostFrequentGenerator generator = new MostFrequentGenerator(fakeSource);

        // 2. Generate a sentence starting with "hello" and a max limit of 20 words
        String result = generator.generate("hello", 20);

        // 3. Verify the output
        // Trace:
        // - Starts with "hello"
        // - "hello" follows to "world" (Valid)
        // - "world" follows to "hello" (Valid, the sequence "world hello" hasn't happened yet)
        // - "hello" looks at "world". The bigram ("hello", "world") already exists at indices 0 and 1!
        // - Loop avoidance triggers, returns null, sentence ends early.

        assertEquals("Hello world hello.", result, "The generator failed to stop the bigram loop!");
    }
}