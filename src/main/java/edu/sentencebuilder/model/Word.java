package edu.sentencebuilder.model;

import java.time.LocalDateTime;

/**
 * One row of the {@code word} table: a distinct lowercase word and how often it
 * appeared overall, at the start of a sentence, and at the end of a sentence.
 * Mutable so the Words Report tab can edit it.
 */
public class Word {
    // Shared by imports and direct DAO writes; keep aligned with word.text VARCHAR(100).
    public static final int MAX_WORD_LENGTH = 100;

    /** Reject oversized text consistently, including supplementary Unicode letters. */
    public static void validateTextLength(String text) {
        // MySQL counts characters, while String.length() counts UTF-16 code units.
        int length = text.codePointCount(0, text.length());
        if (length > MAX_WORD_LENGTH) {
            String preview = text.substring(0, text.offsetByCodePoints(0, 30)) + "…";
            throw new IllegalArgumentException("Word exceeds the " + MAX_WORD_LENGTH
                    + "-character limit (" + length + " characters): \"" + preview + "\".");
        }
    }

    private int wordId;
    private String text;
    private int totalCount;
    private int startCount;
    private int endCount;
    private LocalDateTime firstSeen;

    public Word() {
    }

    public Word(int wordId, String text, int totalCount, int startCount, int endCount, LocalDateTime firstSeen) {
        this.wordId = wordId;
        this.text = text;
        this.totalCount = totalCount;
        this.startCount = startCount;
        this.endCount = endCount;
        this.firstSeen = firstSeen;
    }

    public int getWordId() { return wordId; }
    public void setWordId(int wordId) { this.wordId = wordId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getStartCount() { return startCount; }
    public void setStartCount(int startCount) { this.startCount = startCount; }

    public int getEndCount() { return endCount; }
    public void setEndCount(int endCount) { this.endCount = endCount; }

    public LocalDateTime getFirstSeen() { return firstSeen; }
    public void setFirstSeen(LocalDateTime firstSeen) { this.firstSeen = firstSeen; }

    @Override
    public String toString() {
        return "Word{" + text + ", total=" + totalCount + ", start=" + startCount + ", end=" + endCount + "}";
    }
}
