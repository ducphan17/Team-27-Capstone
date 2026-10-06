package edu.sentencebuilder.generator;

import java.util.List;
import java.util.Random;

/**
 * Picks each next word at random, with probability proportional to
 * {@code count + user_count}. Words the user often chooses in Autocomplete
 * therefore become more likely here too.
 */
public class WeightedRandomGenerator extends SentenceGenerator {

    public WeightedRandomGenerator(WordStatsSource source) {
        super(source);
    }

    public WeightedRandomGenerator(WordStatsSource source, Random random) {
        super(source, random);
    }

    @Override
    public String getName() {
        return "Weighted random";
    }

    @Override
    protected String chooseNext(String current, List<Follower> followers, List<String> sentenceSoFar) {
        long totalWeight = 0;
        for (Follower f : followers) {
            totalWeight += Math.max(0, f.weight());
        }
        if (totalWeight <= 0) {
            return null;
        }
        long draw = random.nextLong(totalWeight);
        for (Follower f : followers) {
            draw -= Math.max(0, f.weight());
            if (draw < 0) {
                return f.text();
            }
        }
        throw new IllegalStateException("unreachable: draw exceeded total weight");
    }
}
