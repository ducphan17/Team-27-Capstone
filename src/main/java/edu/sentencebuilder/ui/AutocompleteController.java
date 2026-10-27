// Tien Phan

package edu.sentencebuilder.ui;

import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.generator.Follower;
import edu.sentencebuilder.parser.AutocompleteService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Autocomplete tab: after a space, comma, or period, suggests the top followers of the
 * last finished word, and records each finished word (typed or picked) through
 * {@link AutocompleteService}. Lookups and saves run on background threads; controls are
 * updated only on the JavaFX Application Thread.
 */
public class AutocompleteController {

    @FXML private TextField inputField;
    @FXML private Label suggestionsLabel;
    @FXML private ListView<String> suggestionList;
    @FXML private Label statusLabel;

    private final WordPairDAO wordPairs = new WordPairDAO(new WordDAO());
    private final AutocompleteService autocomplete = new AutocompleteService();

    // One writer thread saves words in typing order, so saves never compete with each other.
    private final ExecutorService saver = Executors.newSingleThreadExecutor(work -> {
        Thread thread = new Thread(work, "autocomplete-save");
        thread.setDaemon(true); // don't keep the JVM alive after the window closes
        return thread;
    });

    /** Increases with every lookup; a result from an older lookup is stale and ignored. */
    private int lookupGeneration;

    @FXML
    private void initialize() {
        inputField.textProperty().addListener((obs, oldText, newText) -> onTextChanged(oldText, newText));
        suggestionList.setOnMouseClicked(e -> acceptSelectedSuggestion());
        suggestionList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                acceptSelectedSuggestion();
            }
        });
    }

    private void onTextChanged(String oldText, String newText) {
        if (newText.isBlank()) {
            showNoSuggestions("Suggestions appear after a space, comma, or period.");
            return;
        }
        AutocompleteInput.completedWord(oldText, newText).ifPresent(completion -> {
            save(completion);
            if (completion.sentenceEnded()) {
                // Imported pairs never cross a sentence end, so nothing "follows" here.
                showNoSuggestions("Sentence finished. Keep typing to start a new one.");
            } else {
                loadSuggestions(completion.word());
            }
        });
    }

    private void loadSuggestions(String word) {
        int request = ++lookupGeneration;
        suggestionsLabel.setText("Looking up words after \"" + word + "\"…");
        Background.run("autocomplete-lookup",
                // getFollowers is already sorted by count + user_count, highest first.
                () -> wordPairs.getFollowers(word).stream()
                        .limit(AutocompleteInput.MAX_SUGGESTIONS)
                        .map(Follower::text)
                        .toList(),
                suggestions -> {
                    if (request != lookupGeneration) {
                        return; // the user has typed on; a newer lookup owns the list
                    }
                    suggestionList.getItems().setAll(suggestions);
                    suggestionsLabel.setText(suggestions.isEmpty()
                            ? "No words have followed \"" + word + "\" yet."
                            : "Words that often follow \"" + word + "\" (click to use):");
                },
                error -> {
                    if (request != lookupGeneration) {
                        return;
                    }
                    suggestionList.getItems().clear();
                    suggestionsLabel.setText("Could not load suggestions: " + Background.describe(error));
                });
    }

    private void showNoSuggestions(String message) {
        lookupGeneration++; // a lookup still running must not refill the list
        suggestionList.getItems().clear();
        suggestionsLabel.setText(message);
    }

    private void save(AutocompleteInput.Completion completion) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                autocomplete.recordWord(completion.previousWord(), completion.word());
                return null;
            }
        };
        task.setOnSucceeded(e -> statusLabel.setText(""));
        task.setOnFailed(e -> statusLabel.setText("Could not save \"" + completion.word() + "\": "
                + Background.describe(task.getException())));
        saver.execute(task);
    }

    private void acceptSelectedSuggestion() {
        String suggestion = suggestionList.getSelectionModel().getSelectedItem();
        if (suggestion == null) {
            return;
        }
        suggestionList.getSelectionModel().clearSelection();
        // The text listener sees the added word as finished and saves it; saving here too
        // would count every picked word twice.
        inputField.setText(AutocompleteInput.applySuggestion(inputField.getText(), suggestion));
        inputField.requestFocus();
        inputField.positionCaret(inputField.getLength());
    }
}
