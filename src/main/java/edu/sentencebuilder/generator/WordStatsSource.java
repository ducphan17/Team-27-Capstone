package edu.sentencebuilder.generator;

import edu.sentencebuilder.model.Word;

import java.util.List;
import java.util.Optional;

/**
 * Read-only view of word statistics that generators depend on.
 * The real implementation is {@link edu.sentencebuilder.dao.WordPairDAO};
 * tests supply an in-memory fake so generators can be tested without MySQL.
 */
public interface WordStatsSource {

    /** All words that have followed {@code word}; empty list if none or unknown. */
    List<Follower> getFollowers(String word);

    /** The stats row for {@code text}, or empty if the word is unknown. */
    Optional<Word> getWord(String text);
}
