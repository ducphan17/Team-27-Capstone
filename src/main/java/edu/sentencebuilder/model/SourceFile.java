package edu.sentencebuilder.model;

import java.time.LocalDateTime;

/**
 * One row of the {@code source_file} table: a text file that was imported,
 * with its word and sentence totals.
 */
public class SourceFile {
    private int fileId;
    private String filename;
    private int wordCount;
    private int sentenceCount;
    private LocalDateTime importedAt;

    public SourceFile() {
    }

    public SourceFile(String filename, int wordCount, int sentenceCount) {
        this.filename = filename;
        this.wordCount = wordCount;
        this.sentenceCount = sentenceCount;
    }

    public int getFileId() { return fileId; }
    public void setFileId(int fileId) { this.fileId = fileId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public int getWordCount() { return wordCount; }
    public void setWordCount(int wordCount) { this.wordCount = wordCount; }

    public int getSentenceCount() { return sentenceCount; }
    public void setSentenceCount(int sentenceCount) { this.sentenceCount = sentenceCount; }

    public LocalDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }
}
