package edu.sentencebuilder.model;

/**
 * One row of the {@code word_pair} table: how often {@code toWordId} directly
 * followed {@code fromWordId} in imported text ({@code count}) and in the
 * Autocomplete tab ({@code userCount}).
 */
public class WordPair {
    private int fromWordId;
    private int toWordId;
    private int count;
    private int userCount;

    public WordPair() {
    }

    public WordPair(int fromWordId, int toWordId, int count, int userCount) {
        this.fromWordId = fromWordId;
        this.toWordId = toWordId;
        this.count = count;
        this.userCount = userCount;
    }

    public int getFromWordId() { return fromWordId; }
    public void setFromWordId(int fromWordId) { this.fromWordId = fromWordId; }

    public int getToWordId() { return toWordId; }
    public void setToWordId(int toWordId) { this.toWordId = toWordId; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public int getUserCount() { return userCount; }
    public void setUserCount(int userCount) { this.userCount = userCount; }
}
