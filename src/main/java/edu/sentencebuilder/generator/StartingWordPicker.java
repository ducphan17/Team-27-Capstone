//Aalvee
package edu.sentencebuilder.generator;

import java.util.List;
import java.util.Random;

import edu.sentencebuilder.model.Word;

public final class StartingWordPicker {
    private StartingWordPicker() {
        // Prevent instantiation
    }

    public static String pick(List<Word> words, Random random){
        //use long becase the sum can exceed the range of one integer
        long totalWeight = 0;

        for (Word word : words) {
            if (word.getStartCount() > 0) {
                totalWeight += word.getStartCount();
            }
        }

        if (totalWeight == 0) {
            throw new IllegalArgumentException("No starting words available. Import some text first.");
        }

        //each occurrence of a sentence starter gets one ticket
        long ticket = random.nextLong(totalWeight);

        for (Word word : words) {
            if (word.getStartCount() <= 0) {
                continue;
            }

            ticket -= word.getStartCount();

            if (ticket < 0) {
                return word.getText();
            }
        }

        throw new IllegalStateException("Failed to pick a starting word.");
    }
}
