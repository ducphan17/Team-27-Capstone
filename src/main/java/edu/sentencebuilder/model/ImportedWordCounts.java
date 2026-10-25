package edu.sentencebuilder.model;

/** Counts contributed by one import, before a word has a database ID. */
public record ImportedWordCounts(String text, int totalCount, int startCount, int endCount) {
}
