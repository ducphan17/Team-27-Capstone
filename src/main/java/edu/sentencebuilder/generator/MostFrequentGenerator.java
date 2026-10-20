package edu.sentencebuilder.generator;

import java.util.List;
import java.util.Random;
import java.util.ArrayList;

/**
 * Deterministic generator: always picks the follower with the highest
 * {@code count + user_count}.
 *
 * <p>STUB. Always choosing the top follower quickly falls into loops such as
 * "of the of the of the ...": if "the" is the top follower of "of" and "of" is a
 * top follower of "the", the sentence never ends. Before implementing, decide on a loop-avoidance rule, e.g.:
 * <ul>
 *   <li>skip a follower if the pair (current, follower) already appears in {@code sentenceSoFar};</li>
 *   <li>or take the most frequent follower not used in the last N words;</li>
 *   <li>or fall back to the 2nd/3rd most frequent when the top one would repeat.</li>
 * </ul>
 */
public class MostFrequentGenerator extends SentenceGenerator {

    public MostFrequentGenerator(WordStatsSource source) {
        super(source);
    }

    public MostFrequentGenerator(WordStatsSource source, Random random) {
        super(source, random);
    }

    @Override
    public String getName() {
        return "Most frequent";
    }

    @Override
    protected String chooseNext(String current, List<Follower> followers, List<String> sentenceSoFar) {
        // TODO: sort followers by weight() descending and return the first one that
        //       passes your loop-avoidance rule (see class Javadoc). Return null if none do.

        // Create a copy of the list to avoid modifying the original database results
        List<Follower> sortedFollowers = new ArrayList<>(followers);

        // Sort descending by total weight (count + userCount)
        sortedFollowers.sort((f1, f2) -> Integer.compare(f2.weight(), f1.weight()));

        for (Follower candidate : sortedFollowers) {
            String nextWord = candidate.text();
            boolean loopDetected = false;

            // Loop avoidance: Check if the (current, nextWord) pair ia already in the sentence
            for (int i = 0; i < sentenceSoFar.size() - 1; i++) {
                if (sentenceSoFar.get(i).equals(current) && sentenceSoFar.get(i + 1).equals(nextWord)) {
                    loopDetected = true;
                    break;
                }
            }

            // If this bigram hasn't been used yet, it is th highest frequency safe word
            if (!loopDetected) {
                return nextWord;
            }
        }

        // If every single available follower results in a loop, return null to trigger the stopping rule
        return null;
    }
}
