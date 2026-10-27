package edu.sentencebuilder.ui;

import edu.sentencebuilder.dao.SentenceDAO;
import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.generator.MostFrequentGenerator;
import edu.sentencebuilder.generator.SentenceGenerator;
import edu.sentencebuilder.generator.WeightedRandomGenerator;
import edu.sentencebuilder.generator.WordStatsSource;
import edu.sentencebuilder.model.GeneratedSentence;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.util.List;

/**
 * Generate tab: produces one sentence from a user-chosen starting word,
 * algorithm, and maximum length, shows it, and saves it to {@code generated_sentence}.
 * Database work runs on a background {@link Task}; controls are updated only
 * on the JavaFX Application Thread.
 */
public class GenerateController {

    /** Upper bound for the Max words spinner. */
    static final int MAX_WORDS_LIMIT = 100;

    @FXML private TextField startWordField;
    @FXML private ComboBox<SentenceGenerator> algorithmBox;
    @FXML private Spinner<Integer> maxWordsSpinner;
    @FXML private Button generateButton;
    @FXML private Label resultLabel;

    /** DAOs are stateless (a connection per call), so one source serves every generation. */
    private final WordPairDAO source = new WordPairDAO(new WordDAO());

    @FXML
    private void initialize() {
        algorithmBox.getItems().addAll(algorithms(source));
        algorithmBox.setConverter(ALGORITHM_NAMES);
        algorithmBox.getSelectionModel().selectFirst();

        SpinnerValueFactory.IntegerSpinnerValueFactory maxWords =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(
                        1, MAX_WORDS_LIMIT, SentenceGenerator.DEFAULT_MAX_WORDS);
        // The default converter throws on text like "abc"; keep the current value instead.
        maxWords.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : value.toString();
            }

            @Override
            public Integer fromString(String text) {
                Integer parsed = parseMaxWords(text);
                return parsed != null ? parsed : maxWords.getValue();
            }
        });
        maxWordsSpinner.setValueFactory(maxWords);
    }

    @FXML
    private void onGenerate() {
        if (generateButton.isDisabled()) return; // a generation task is already running

        String startWord = startWordField.getText().trim().toLowerCase();
        if (startWord.isEmpty()) {
            resultLabel.setText("Please enter a starting word.");
            return;
        }

        // Read the editor text directly: typed text isn't committed to the spinner
        // until Enter or focus loss, and Enter in the word field skips both.
        Integer maxWords = parseMaxWords(maxWordsSpinner.getEditor().getText());
        if (maxWords == null) {
            resultLabel.setText("Max words must be a whole number from 1 to " + MAX_WORDS_LIMIT + ".");
            return;
        }
        maxWordsSpinner.getValueFactory().setValue(maxWords);

        // Safe to reuse across clicks: only one generation task runs at a time.
        SentenceGenerator generator = algorithmBox.getValue();

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                // Unknown word → fail with a clear message instead of returning "Xyz."
                if (source.getWord(startWord).isEmpty()) {
                    throw new IllegalArgumentException(
                            "\"" + startWord + "\" isn't in the database yet. Import a text that contains it.");
                }
                String sentence = generator.generate(startWord, maxWords);
                new SentenceDAO().insert(new GeneratedSentence(sentence, startWord, generator.getName()));
                return sentence;
            }
        };

        task.setOnSucceeded(e -> {
            resultLabel.setText(task.getValue());
            generateButton.setDisable(false);
        });
        task.setOnFailed(e -> {
            Throwable error = task.getException();
            resultLabel.setText("Could not generate: "
                    + (error == null ? "unknown error" : error.getMessage()));
            generateButton.setDisable(false);
        });

        generateButton.setDisable(true);
        resultLabel.setText("Generating…");

        Thread worker = new Thread(task, "generate-sentence");
        worker.setDaemon(true);
        worker.start();
    }

    /** Shows each generator by {@link SentenceGenerator#getName()}, the same label saved with its sentences. */
    static final StringConverter<SentenceGenerator> ALGORITHM_NAMES = new StringConverter<>() {
        @Override
        public String toString(SentenceGenerator generator) {
            return generator == null ? "" : generator.getName();
        }

        @Override
        public SentenceGenerator fromString(String text) {
            // Only called for editable combo boxes; the algorithm box is not editable.
            throw new UnsupportedOperationException();
        }
    };

    /** The algorithm choices in display order; the first is the default. */
    static List<SentenceGenerator> algorithms(WordStatsSource source) {
        return List.of(new WeightedRandomGenerator(source), new MostFrequentGenerator(source));
    }

    /**
     * Parses the Max words text.
     *
     * @return a whole number from 1 to {@link #MAX_WORDS_LIMIT}, or {@code null} if the text is not one
     */
    static Integer parseMaxWords(String text) {
        if (text == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(text.trim());
            return value >= 1 && value <= MAX_WORDS_LIMIT ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
