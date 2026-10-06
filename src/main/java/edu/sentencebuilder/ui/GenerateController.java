package edu.sentencebuilder.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Generate tab: produces one sentence from a user-chosen starting word and
 * algorithm, shows it, and saves it to {@code generated_sentence}.
 * PLACEHOLDER — layout only.
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
        // TODO: in a javafx.concurrent.Task (generators hit the DB through WordPairDAO):
        //   1. WordDAO wordDAO = new WordDAO(); WordStatsSource source = new WordPairDAO(wordDAO);
        //   2. pick WeightedRandomGenerator / MostFrequentGenerator from algorithmBox
        //   3. String s = generator.generate(startWordField.getText());
        //   4. new SentenceDAO().insert(new GeneratedSentence(s, startWord, generator.getName()));
        //   then show s in resultLabel from setOnSucceeded (FX thread).
        resultLabel.setText("TODO: not implemented yet");
    }
}
