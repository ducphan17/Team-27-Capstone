package edu.sentencebuilder.ui;

import edu.sentencebuilder.dao.SentenceDAO;
import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.generator.MostFrequentGenerator;
import edu.sentencebuilder.generator.SentenceGenerator;
import edu.sentencebuilder.generator.WeightedRandomGenerator;
import edu.sentencebuilder.model.GeneratedSentence;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Generate tab: produces one sentence from a user-chosen starting word and
 * algorithm, shows it, and saves it to {@code generated_sentence}.
 * Database work runs on a background {@link Task}; controls are updated only
 * on the JavaFX Application Thread.
 */
public class GenerateController {

    @FXML private TextField startWordField;
    @FXML private ComboBox<String> algorithmBox;
    @FXML private Button generateButton;
    @FXML private Label resultLabel;

    @FXML
    private void initialize() {
        // TODO: consider a ComboBox<SentenceGenerator> with a StringConverter using getName().
        algorithmBox.getItems().addAll("Weighted random", "Most frequent");
        algorithmBox.getSelectionModel().selectFirst();
    }

    @FXML
    private void onGenerate() {
        if (generateButton.isDisabled()) return; // a generation task is already running

        String startWord = startWordField.getText().trim().toLowerCase();
        if (startWord.isEmpty()) {
            resultLabel.setText("Please enter a starting word.");
            return;
        }

        WordDAO wordDAO = new WordDAO();
        WordPairDAO source = new WordPairDAO(wordDAO);

        SentenceGenerator generator =
                "Most frequent".equals(algorithmBox.getValue())
                        ? new MostFrequentGenerator(source)
                        : new WeightedRandomGenerator(source);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                // Unknown word → fail with a clear message instead of returning "Xyz."
                if (source.getWord(startWord).isEmpty()) {
                    throw new IllegalArgumentException(
                            "\"" + startWord + "\" isn't in the database yet. Import a text that contains it.");
                }
                String sentence = generator.generate(startWord);
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
}
