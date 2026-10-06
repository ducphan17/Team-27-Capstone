package edu.sentencebuilder.generator;

/**
 * A word that can follow another word, with its corpus and user counts.
 * This is what generators see instead of raw {@code word_pair} rows, so they
 * work with text and never with database IDs.
 *
 * @param text      the following word
 * @param count     times it followed in imported text
 * @param userCount times it was chosen in the Autocomplete tab
 */
public record Follower(String text, int count, int userCount) {

    /** Combined weight used for weighted choices. */
    public int weight() {
        return count + userCount;
    }
}
