package edu.sentencebuilder.model;

import java.time.LocalDateTime;

/**
 * One row of the {@code generated_sentence} table: a sentence produced by a
 * generator, which algorithm made it, and whether it was fed back into the corpus.
 */
public class GeneratedSentence {
    private int id;
    private String sentence;
    private String startWord;
    private String algorithm;
    private LocalDateTime createdAt;
    private boolean addedToCorpus;

    public GeneratedSentence() {
    }

    public GeneratedSentence(String sentence, String startWord, String algorithm) {
        this.sentence = sentence;
        this.startWord = startWord;
        this.algorithm = algorithm;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getSentence() { return sentence; }
    public void setSentence(String sentence) { this.sentence = sentence; }

    public String getStartWord() { return startWord; }
    public void setStartWord(String startWord) { this.startWord = startWord; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isAddedToCorpus() { return addedToCorpus; }
    public void setAddedToCorpus(boolean addedToCorpus) { this.addedToCorpus = addedToCorpus; }
}
