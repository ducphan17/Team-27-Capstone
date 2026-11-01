//Aalvee

package edu.sentencebuilder.ui;

import edu.sentencebuilder.dao.Database;
import edu.sentencebuilder.dao.DataAccessException;
import edu.sentencebuilder.dao.SentenceDAO;
import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.generator.MostFrequentGenerator;
import edu.sentencebuilder.generator.Follower;
import edu.sentencebuilder.generator.SentenceGenerator;
import edu.sentencebuilder.generator.StartingWordPicker;
import edu.sentencebuilder.generator.WeightedRandomGenerator;
import edu.sentencebuilder.generator.WordStatsSource;
import edu.sentencebuilder.model.GeneratedSentence;
import edu.sentencebuilder.model.Word;
import edu.sentencebuilder.parser.CorpusService;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Spinner;
import javafx.util.StringConverter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Generates and saves batches in the background.
 * Selected results can be imported into the corpus once.
 */
public class BatchGenerateController {

    @FXML private Spinner<Integer> countSpinner;
    @FXML private ComboBox<String> algorithmBox;
    @FXML private ListView<GeneratedSentence> sentenceList;
    @FXML private Button generateButton;
    @FXML private Button addToCorpusButton;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;

    private boolean busy;

    /**
     * Carries successfully saved rows even if a later sentence fails.
     * This prevents saved results from disappearing after a partial failure.
     */
    private record BatchResult(
            List<GeneratedSentence> sentences,
            Exception failure) {
    }

    @FXML
    private void initialize() {
        algorithmBox.getItems().addAll(
                "Weighted random", "Most frequent");
        algorithmBox.getSelectionModel().selectFirst();

        // Allow several rows to be selected with Ctrl/Cmd or Shift.
        sentenceList.getSelectionModel()
                .setSelectionMode(SelectionMode.MULTIPLE);

        // Keep model objects in the list so their database IDs remain available.
        sentenceList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(
                    GeneratedSentence sentence, boolean empty) {

                super.updateItem(sentence, empty);

                setText(empty || sentence == null
                        ? null
                        : sentence.getSentence()
                            + (sentence.isAddedToCorpus()
                                ? " [Added to corpus]" : ""));
            }
        });

        sentenceList.getSelectionModel().getSelectedItems()
                .addListener(
                        (javafx.collections.ListChangeListener<GeneratedSentence>)
                                change -> updateAddButton());

        // Avoid NumberFormatException when the editable spinner contains text.
        var factory = countSpinner.getValueFactory();

        factory.setConverter(new StringConverter<Integer>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : value.toString();
            }

            @Override
            public Integer fromString(String text) {
                Integer parsed = parseCount(text);
                return parsed == null ? factory.getValue() : parsed;
            }
        });

        updateAddButton();
    }

    //Aalvee
    @FXML
    private void onGenerate() {
        if (busy) {
            return;
        }

        // Read the editor because typed values may not be committed yet.
        Integer count = parseCount(countSpinner.getEditor().getText());
        if (count == null) {
            statusLabel.setText(
                    "Number of sentences must be a whole number from 1 to 1000.");
            return;
        }

        countSpinner.getValueFactory().setValue(count);

        // Capture control values before starting the worker thread.
        String algorithm = algorithmBox.getValue();

        Task<BatchResult> task = new Task<>() {
            @Override
            protected BatchResult call() {
                // Indeterminate progress while opening the connection/loading starters.
                updateProgress(-1, 1);
                updateMessage("Loading starting words…");

                return generateBatch(count, algorithm, (saved, total) -> {
                    // Task safely forwards these updates to the FX thread.
                    updateProgress(saved, total);
                    updateMessage(
                            "Saved " + saved + " of " + total + " sentences.");
                });
            }
        };

        setBusy(true);
        progressBar.setProgress(-1);
        statusLabel.setText("Starting batch…");

        // Bind before starting so even a quick task is handled correctly.
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(event -> {
            // Unbind before assigning final values to the controls.
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();

            BatchResult result = task.getValue();
            int saved = result.sentences().size();

            sentenceList.getItems().setAll(result.sentences());
            progressBar.setProgress((double) saved / count);
            setBusy(false);

            if (result.failure() == null) {
                statusLabel.setText(
                        "Generated and saved " + saved + " sentences.");
            } else {
                statusLabel.setText(
                        "Saved " + saved + " of " + count + " sentences. "
                                + Background.describe(result.failure()));
            }
        });

        task.setOnFailed(event -> {
            // Also release bindings and controls after an unexpected task failure.
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();

            progressBar.setProgress(0);
            setBusy(false);
            statusLabel.setText(
                    "Could not generate: "
                            + Background.describe(task.getException()));
        });

        Thread worker = new Thread(task, "batch-generate");
        worker.setDaemon(true);
        worker.start();
    }

    //Aalvee
    /**
     * Generates and saves on the worker thread using one connection.
     * Reports progress only after a sentence has been saved successfully.
     */
    private BatchResult generateBatch(
            int count,
            String algorithm,
            BiConsumer<Integer, Integer> reportProgress) {

        List<GeneratedSentence> saved = new ArrayList<>();
        WordDAO words = new WordDAO();
        WordPairDAO pairs = new WordPairDAO(words);
        SentenceDAO sentences = new SentenceDAO();
        Random random = new Random();

        try (Connection conn = Database.getConnection()) {
            // Each successful insert stays saved if a later sentence fails.
            conn.setAutoCommit(true);

            List<Word> starters = words.findStartingWords(conn);
            if (starters.isEmpty()) {
                throw new IllegalArgumentException(
                        "No starting words available. Import some text first.");
            }

            // Generators still depend only on WordStatsSource.
            // This adapter routes their lookups through the batch's connection.
            WordStatsSource source = new WordStatsSource() {
                @Override
                public Optional<Word> getWord(String text) {
                    try {
                        return words.findByText(conn, text);
                    } catch (SQLException e) {
                        throw new DataAccessException(
                                "Could not load word '" + text + "'", e);
                    }
                }

                @Override
                public List<Follower> getFollowers(String word) {
                    try {
                        return pairs.getFollowers(conn, word);
                    } catch (SQLException e) {
                        throw new DataAccessException(
                                "Could not load followers of '" + word + "'", e);
                    }
                }
            };

            SentenceGenerator generator = "Most frequent".equals(algorithm)
                    ? new MostFrequentGenerator(source)
                    : new WeightedRandomGenerator(source);

            for (int i = 0; i < count; i++) {
                String startWord = StartingWordPicker.pick(starters, random);
                String text = generator.generate(startWord);

                GeneratedSentence sentence = new GeneratedSentence(
                        text, startWord, generator.getName());

                sentences.insert(conn, sentence);

                // Only include confirmed saves in results and progress.
                saved.add(sentence);
                reportProgress.accept(saved.size(), count);
            }
        } catch (Exception error) {
            // Return earlier saves so a partial failure doesn't hide them.
            return new BatchResult(saved, error);
        }

        return new BatchResult(saved, null);
    }

    @FXML
    private void onAddToCorpus() {
        if (busy) {
            return;
        }

        // Snapshot the selection on the FX thread.
        // Already-added rows are excluded from subsequent imports.
        List<GeneratedSentence> selected =
                sentenceList.getSelectionModel()
                        .getSelectedItems()
                        .stream()
                        .filter(sentence -> !sentence.isAddedToCorpus())
                        .toList();

        if (selected.isEmpty()) {
            statusLabel.setText(
                    "Select sentences that have not been added yet.");
            return;
        }

        String text = selected.stream()
                .map(GeneratedSentence::getSentence)
                .collect(Collectors.joining("\n"));

        List<Integer> ids = selected.stream()
                .map(GeneratedSentence::getId)
                .toList();

        setBusy(true);
        statusLabel.setText("Adding selected sentences to corpus…");

        Background.run(
                "batch-add-to-corpus",
                () -> {
                    new CorpusService().addToCorpus(text, ids);
                    return selected.size();
                },
                addedCount -> {
                    // Change the visible state only after commit succeeds.
                    selected.forEach(
                            sentence -> sentence.setAddedToCorpus(true));

                    sentenceList.refresh();
                    setBusy(false);
                    statusLabel.setText(
                            "Added " + addedCount + " sentences to corpus.");
                },
                error -> {
                    setBusy(false);
                    statusLabel.setText(
                            "Could not add to corpus: "
                                    + Background.describe(error));
                });
    }

    private void setBusy(boolean value) {
        busy = value;
        generateButton.setDisable(value);
        countSpinner.setDisable(value);
        algorithmBox.setDisable(value);
        sentenceList.setDisable(value);
        updateAddButton();
    }

    private void updateAddButton() {
        boolean hasEligibleSelection =
                sentenceList.getSelectionModel()
                        .getSelectedItems()
                        .stream()
                        .anyMatch(sentence -> !sentence.isAddedToCorpus());

        addToCorpusButton.setDisable(busy || !hasEligibleSelection);
    }

    /** Returns null for blank, nonnumeric, or out-of-range input. */
    static Integer parseCount(String text) {
        if (text == null) {
            return null;
        }

        try {
            int value = Integer.parseInt(text.trim());
            return value >= 1 && value <= 1000 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
