package edu.sentencebuilder.ui;

import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.generator.Follower;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Autocomplete tab: suggests the top followers of the last completed word and
 * records the user's choices in {@code word_pair.user_count}.
 */
public class AutocompleteController {

    @FXML private TextField inputField;
    @FXML private ListView<String> suggestionList;

    private WordDAO wordDao;
    private WordPairDAO wordPairDao;

    // Tracks the currently running task so we can cancel it if the user types quickly
    private Task<List<Follower>> activeSearchTask;
    private String lastCompletedWord = null;

    public void setDaos(WordDAO wordDao, WordPairDAO wordPairDao) {
        this.wordDao = wordDao;
        this.wordPairDao = wordPairDao;
    }

    @FXML
    private void initialize() {
        // Listen for new text and trigger search on ' ', ',', or '.'
        inputField.textProperty().addListener((obs, oldText, newText) -> {
            if (newText == null || newText.isEmpty()) {
                suggestionList.getItems().clear();
                return;
            }

            char lastChar = newText.charAt(newText.length() - 1);
            if (lastChar == ' ' || lastChar == ',' || lastChar == '.') {
                String[] words = newText.split("[\\s,\\.]+");
                if (words.length > 0) {
                    lastCompletedWord = words[words.length - 1].toLowerCase();
                    fetchSuggestions(lastCompletedWord);
                }
            } else {
                suggestionList.getItems().clear();
            }
        });

        // Handle suggestion selection
        suggestionList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selectedWord) -> {
            if (selectedWord != null) {
                applySuggestion(selectedWord);
            }
        });
    }

    private void fetchSuggestions(String word) {
        if (activeSearchTask != null && activeSearchTask.isRunning()) {
            activeSearchTask.cancel();
        }

        activeSearchTask = new Task<>() {
            @Override
            protected List<Follower> call() throws Exception {
                // Assuming getFollowers is available, or you use WordStatsSource
                // Sort by weight descending and limit to top 5
                return wordPairDao.getFollowers(word).stream()
                        .sorted((f1, f2) -> Integer.compare(f2.weight(), f1.weight()))
                        .limit(5)
                        .collect(Collectors.toList());
            }
        };

        activeSearchTask.setOnSucceeded(e -> {
            List<Follower> results = activeSearchTask.getValue();
            suggestionList.getItems().clear();
            for (Follower f : results) {
                suggestionList.getItems().add(f.text());
            }
        });

        activeSearchTask.setOnFailed(e -> {
            Throwable error = activeSearchTask.getException();
            System.err.println("Failed to fetch suggestions: " + error.getMessage());
        });

        // Run the task on a background daemon thread
        Thread thread = new Thread(activeSearchTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void applySuggestion(String selectedWord) {
        String currentText = inputField.getText();

        // Append the selected word. Adjust spacing based on trailing punctuation if necessary.
        String newText = currentText + selectedWord + " ";

        Platform.runLater(() -> {
            inputField.setText(newText);
            inputField.positionCaret(newText.length());
            suggestionList.getItems().clear();
        });

        // Record the choice in the background to avoid freezing the UI
        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (lastCompletedWord != null) {
                    int prevWordId = wordDao.insertIfAbsent(lastCompletedWord);
                    int currentWordId = wordDao.insertIfAbsent(selectedWord.toLowerCase());
                    wordPairDao.incrementUserCount(prevWordId, currentWordId);
                }
                return null;
            }
        };

        Thread saveThread = new Thread(saveTask);
        saveThread.setDaemon(true);
        saveThread.start();

        // Update last completed word to the newly selected word to chain suggestions
        lastCompletedWord = selectedWord.toLowerCase();
        fetchSuggestions(lastCompletedWord);
    }
}