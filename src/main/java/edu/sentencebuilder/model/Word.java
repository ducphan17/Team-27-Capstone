package edu.sentencebuilder.model;

import java.time.LocalDateTime;

/**
 * One row of the {@code word} table: a distinct lowercase word and how often it
 * appeared overall, at the start of a sentence, and at the end of a sentence.
 * Mutable so the Words Report tab can edit it.
 */
public class Word {
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
