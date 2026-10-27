// Tien Phan

package edu.sentencebuilder.ui;

import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.generator.Follower;
import edu.sentencebuilder.parser.AutocompleteService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Autocomplete tab: after a space, comma, or period, suggests the top followers of the
 * last finished word with their imported and typed counts. Each sentence is saved through
 * {@link AutocompleteService} only once it ends (or Enter is pressed), so typos corrected
 * before then are never stored. Lookups and saves run on background threads; controls are
 * updated only on the JavaFX Application Thread.
 */
public class AutocompleteController {

    @FXML private TextField inputField;
    @FXML private Label suggestionsLabel;
    @FXML private ListView<Follower> suggestionList;
    @FXML private Label statusLabel;

    private final WordPairDAO wordPairs = new WordPairDAO(new WordDAO());
    private final AutocompleteService autocomplete = new AutocompleteService();

    // One writer thread saves sentences in typing order, so saves never compete with each other.
    private final ExecutorService saver = Executors.newSingleThreadExecutor(work -> {
        Thread thread = new Thread(work, "autocomplete-save");
        thread.setDaemon(true); // don't keep the JVM alive after the window closes
        return thread;
    });

    /** Increases with every lookup; a result from an older lookup is stale and ignored. */
    private int lookupGeneration;

    /** The start of the field that has already been saved; empty or ending at a sentence end. */
    private String savedText = "";

    @FXML
    private void initialize() {
        suggestionList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Follower follower, boolean empty) {
                super.updateItem(follower, empty);
                setText(empty || follower == null ? null : follower.text()
                        + "    (imported " + follower.count() + " · typed " + follower.userCount() + ")");
            }
        });
        inputField.textProperty().addListener((obs, oldText, newText) -> onTextChanged(oldText, newText));
        inputField.setOnAction(e -> saveAndClear());
        suggestionList.setOnMouseClicked(e -> acceptSelectedSuggestion());
        suggestionList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                acceptSelectedSuggestion();
            }
        });
    }

    private void onTextChanged(String oldText, String newText) {
        if (newText.isBlank()) {
            savedText = "";
            showNoSuggestions("Suggestions appear after a space, comma, or period.");
            return;
        }
        boolean sentenceEnded = AutocompleteInput.endsSentence(oldText, newText);
        boolean anythingNew = false;
        if (sentenceEnded) {
            String finished = AutocompleteInput.finishedPart(newText);
            List<List<String>> sentences = AutocompleteInput.unsavedSentences(savedText, finished);
            anythingNew = !sentences.isEmpty();
            save(sentences);
            savedText = finished;
        }
        // Read inside the lambda, so it must be effectively final.
        boolean newSentence = anythingNew;
        AutocompleteInput.completedWord(oldText, newText).ifPresentOrElse(
                this::loadSuggestions,
                () -> {
                    if (sentenceEnded) {
                        // Imported pairs never cross a sentence end, so nothing "follows" here.
                        // The status label confirms the save once it actually finishes.
                        showNoSuggestions(newSentence
                                ? "Sentence finished. Keep typing to start a new one."
                                : "This sentence was already saved. Keep typing to start a new one.");
                    }
                });
    }

    /** Enter saves a sentence that has no end mark yet and starts over with an empty field. */
    private void saveAndClear() {
        save(AutocompleteInput.unsavedSentences(savedText, inputField.getText()));
        inputField.clear(); // the text listener resets savedText
    }

    private void loadSuggestions(String word) {
        int request = ++lookupGeneration;
        suggestionsLabel.setText("Looking up words after \"" + word + "\"…");
        Background.run("autocomplete-lookup",
                // getFollowers is already sorted by count + user_count, highest first.
                () -> wordPairs.getFollowers(word).stream()
                        .limit(AutocompleteInput.MAX_SUGGESTIONS)
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

    private void save(List<List<String>> sentences) {
        if (sentences.isEmpty()) {
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                autocomplete.recordSentences(sentences);
                return null;
            }
        };
        String saved = sentences.stream()
                .map(words -> "\"" + String.join(" ", words) + "\"")
                .collect(Collectors.joining(", "));
        task.setOnSucceeded(e -> statusLabel.setText("Saved " + saved + "."));
        task.setOnFailed(e -> statusLabel.setText("Could not save " + saved + ": "
                + Background.describe(task.getException())));
        saver.execute(task);
    }

    private void acceptSelectedSuggestion() {
        Follower suggestion = suggestionList.getSelectionModel().getSelectedItem();
        if (suggestion == null) {
            return;
        }
        suggestionList.getSelectionModel().clearSelection();
        // Only the text changes here; the word is counted once, when its sentence is saved.
        inputField.setText(AutocompleteInput.applySuggestion(inputField.getText(), suggestion.text()));
        inputField.requestFocus();
        inputField.positionCaret(inputField.getLength());
    }
}
