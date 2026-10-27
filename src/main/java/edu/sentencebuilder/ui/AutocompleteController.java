// Tien Phan

package edu.sentencebuilder.ui;

import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.generator.Follower;
import edu.sentencebuilder.parser.AutocompleteService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Autocomplete tab: suggests the top followers of the last finished word, with their
 * imported and typed counts, and keeps the list in step with the text (including
 * backspacing). Each sentence is saved through {@link AutocompleteService} once it ends
 * with {@code . ! ?} or Enter adds a period, so typos corrected before then are never
 * stored. {@link AutocompleteSaves} makes sure every sentence is saved exactly once and
 * retries failed saves; {@link PendingSentences} keeps unconfirmed ones across restarts.
 * Lookups, saves, and file writes run on background threads; controls are touched only on
 * the JavaFX Application Thread.
 */
public class AutocompleteController {

    /** Seconds to wait before retrying sentences whose save failed. */
    static final int RETRY_SECONDS = 10;

    /** Seconds the app waits on close for a running save to finish. */
    private static final int SHUTDOWN_WAIT_SECONDS = 5;

    /** Milliseconds of typing pause before followers are looked up, so not every keystroke queries. */
    private static final int LOOKUP_DELAY_MS = 150;

    private static final String HINT = "Suggestions appear as you type a word that has been followed before.";

    @FXML private TextField inputField;
    @FXML private Label suggestionsLabel;
    @FXML private ListView<Follower> suggestionList;
    @FXML private Label statusLabel;

    private final WordPairDAO wordPairs = new WordPairDAO(new WordDAO());
    private final AutocompleteService autocomplete = new AutocompleteService();
    private final AutocompleteSaves saves = new AutocompleteSaves();

    // One writer thread runs saves and pending-file writes in order, so they never compete.
    private final ExecutorService saver = Executors.newSingleThreadExecutor(work -> {
        Thread thread = new Thread(work, "autocomplete-save");
        thread.setDaemon(true); // don't keep the JVM alive after the window closes
        return thread;
    });
    private final PauseTransition retryTimer = new PauseTransition(Duration.seconds(RETRY_SECONDS));
    private final PauseTransition lookupDelay = new PauseTransition(Duration.millis(LOOKUP_DELAY_MS));

    /** Increases with every lookup; a result from an older lookup is stale and ignored. */
    private int lookupGeneration;
    /** The word whose followers are shown or loading; {@code null} when none (or its lookup failed). */
    private String suggestionsFor;
    /** True while Enter adds a period, so the text change is handled as a sentence end. */
    private boolean endingSentence;

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
        inputField.setOnAction(e -> onEnter());
        suggestionList.setOnMouseClicked(e -> acceptSelectedSuggestion());
        suggestionList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                acceptSelectedSuggestion();
            }
        });
        retryTimer.setOnFinished(e -> submit(saves.takeWaiting()));
        lookupDelay.setOnFinished(e -> loadSuggestions(suggestionsFor));
        restorePendingSentences();
        // On close, let a running save and its pending-file update finish instead of
        // stopping them halfway. Daemon threads keep running while shutdown hooks run.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            saver.shutdown();
            try {
                saver.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                // Exiting anyway; the pending file still lists anything unconfirmed.
            }
        }));
    }

    private void onTextChanged(String oldText, String newText) {
        if (newText.isBlank()) {
            saves.fieldCleared();
            showNoSuggestions(HINT);
            return;
        }
        String sentenceMessage = null;
        if (endingSentence || AutocompleteInput.endsSentence(oldText, newText)) {
            saveNewSentences(AutocompleteInput.finishedPart(newText));
            sentenceMessage = "Sentence finished. Keep typing to start a new one.";
        }
        updateSuggestions(AutocompleteInput.contextWord(newText).orElse(null), sentenceMessage);
    }

    /**
     * Enter ends the current sentence the same way typing a period does, keeping the text so
     * the user can see what was saved. It also retries waiting sentences right away.
     */
    private void onEnter() {
        String text = inputField.getText();
        String ended = AutocompleteInput.withEndMark(text);
        if (!ended.equals(text)) {
            endingSentence = true;
            try {
                inputField.setText(ended); // the text listener saves the sentence
            } finally {
                endingSentence = false;
            }
            inputField.positionCaret(ended.length());
        } else if (!text.isBlank()) {
            saveNewSentences(text); // nothing to end; still save sentences edited since
        }
        submit(saves.takeWaiting());
    }

    /** Saves what is new in {@code text}, or says it was already saved. */
    private void saveNewSentences(String text) {
        AutocompleteSaves.Batch batch = saves.take(text);
        if (batch.isEmpty() && batch.rejected().isEmpty()) {
            // Shown with the other save messages; submit reports everything else.
            statusLabel.setText("This sentence was already saved.");
        }
        submit(batch);
    }

    /**
     * Shows followers of {@code word}, reloading after a short typing pause only when the word
     * changed or its lookup failed. The old list is cleared at once, so followers of a
     * different word can never be picked.
     */
    private void updateSuggestions(String word, String sentenceMessage) {
        if (word == null) {
            // Imported pairs never cross a sentence end, so nothing "follows" after one.
            if (sentenceMessage != null) {
                showNoSuggestions(sentenceMessage);
            } else if (suggestionsFor != null) {
                showNoSuggestions(HINT); // e.g. backspaced past the only word
            }
            return;
        }
        if (!word.equals(suggestionsFor)) {
            lookupGeneration++; // a lookup still running is for another word
            suggestionsFor = word;
            suggestionList.getItems().clear();
            suggestionsLabel.setText("Looking up words after \"" + word + "\"…");
            lookupDelay.playFromStart();
        }
    }

    private void loadSuggestions(String word) {
        int request = ++lookupGeneration;
        Background.run("autocomplete-lookup",
                // getFollowers is already sorted by count + user_count, highest first.
                () -> wordPairs.getFollowers(word).stream()
                        .limit(AutocompleteInput.MAX_SUGGESTIONS)
                        .toList(),
                suggestions -> {
                    if (request != lookupGeneration) {
                        return; // the text has changed; a newer lookup owns the list
                    }
                    suggestionList.getItems().setAll(suggestions);
                    suggestionsLabel.setText(suggestions.isEmpty()
                            ? "No words have followed \"" + word + "\" yet."
                            : "Words that often follow \"" + word + "\" (click to add):");
                },
                error -> {
                    if (request != lookupGeneration) {
                        return;
                    }
                    suggestionsFor = null; // the next edit looks it up again
                    suggestionsLabel.setText("Could not load suggestions: " + Background.describe(error)
                            + ". Keep typing to try again.");
                });
    }

    private void showNoSuggestions(String message) {
        lookupDelay.stop();
        lookupGeneration++; // a lookup still running must not refill the list
        suggestionsFor = null;
        suggestionList.getItems().clear();
        suggestionsLabel.setText(message);
    }

    /** Saves a batch on the save thread; on failure its sentences wait for a retry. */
    private void submit(AutocompleteSaves.Batch batch) {
        String rejectedNote = batch.rejected().isEmpty()
                ? "" : " Not saved: " + String.join(" ", batch.rejected());
        if (batch.isEmpty()) {
            if (!rejectedNote.isEmpty()) {
                statusLabel.setText(rejectedNote.strip());
            }
            return;
        }
        if (batch.retry()) {
            retryTimer.stop(); // this batch carries every waiting sentence
        }

        String described = describe(batch.sentences());
        statusLabel.setText("Saving " + described + "…" + rejectedNote);
        Background.run(saver,
                // The save thread records the outcome and updates the pending file around the
                // attempt, so the file is right even if the window closes before the callbacks.
                () -> {
                    rememberUnconfirmed(); // before: closing mid-save keeps the batch
                    try {
                        autocomplete.recordSentences(batch.sentences());
                    } catch (Exception error) {
                        saves.failed(batch);
                        rememberUnconfirmed();
                        throw error;
                    }
                    saves.succeeded(batch);
                    rememberUnconfirmed(); // after: a committed batch is never retried
                    return null;
                },
                ignored -> statusLabel.setText("Saved " + described + "." + rejectedNote + waitingNote()),
                error -> {
                    statusLabel.setText("Could not save " + described + ": " + Background.describe(error)
                            + "." + waitingNote() + rejectedNote);
                    if (saves.retriesAutomatically()) {
                        retryTimer.playFromStart();
                    }
                });
    }

    /** Tells the user how many sentences wait for a retry, and how they will be retried. */
    private String waitingNote() {
        int waiting = saves.waitingCount();
        if (waiting == 0) {
            return "";
        }
        String count = " " + waiting + (waiting == 1 ? " sentence is" : " sentences are") + " waiting; ";
        return saves.retriesAutomatically()
                ? count + "retrying in " + RETRY_SECONDS + " seconds, or press Enter to retry now."
                : count + "automatic retries stopped after " + AutocompleteSaves.MAX_AUTOMATIC_RETRIES
                        + " attempts. Press Enter to retry.";
    }

    /** Quoted sentences for the status label, or just their number when there are many. */
    private static String describe(List<List<String>> sentences) {
        if (sentences.size() > 3) {
            return sentences.size() + " sentences";
        }
        return sentences.stream()
                .map(words -> "\"" + String.join(" ", words) + "\"")
                .collect(Collectors.joining(", "));
    }

    /**
     * Writes the unconfirmed sentences to the pending file. Runs on the save thread only, so
     * writes happen in order. A failed write is reported but never blocks the save itself.
     */
    private void rememberUnconfirmed() {
        try {
            PendingSentences.write(PendingSentences.FILE, saves.unconfirmed());
        } catch (IOException e) {
            Platform.runLater(() -> statusLabel.setText("Could not remember unsaved sentences in "
                    + PendingSentences.FILE + ": " + Background.describe(e)));
        }
    }

    /** Retries sentences an earlier run of the app could not save. */
    private void restorePendingSentences() {
        // On the save thread, so the file is read before any save can rewrite it.
        Background.run(saver,
                () -> PendingSentences.read(PendingSentences.FILE),
                sentences -> {
                    if (!sentences.isEmpty()) {
                        saves.restore(sentences);
                        submit(saves.takeWaiting()); // shows "Saving …" with the sentences
                    }
                },
                error -> statusLabel.setText("Could not read unsaved sentences from "
                        + PendingSentences.FILE + ": " + Background.describe(error)));
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
