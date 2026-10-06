package edu.sentencebuilder.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;

/**
 * Batch Generate tab: generates N sentences from random starting words
 * (weighted by start_count) and can feed them back into the corpus.
 * PLACEHOLDER — layout only.
 */
public class BatchGenerateController {

    @FXML private Spinner<Integer> countSpinner;
    @FXML private ComboBox<String> algorithmBox;
    @FXML private ListView<String> sentenceList;
    @FXML private Button addToCorpusButton;

    @FXML
    private void initialize() {
        algorithmBox.getItems().addAll("Weighted random", "Most frequent");
        algorithmBox.getSelectionModel().selectFirst();
    }

    @FXML
    private void onGenerate() {
        // TODO: in a background Task:
        //   - pick N start words weighted by start_count (needs a new WordDAO query,
        //     e.g. SELECT text, start_count FROM word WHERE start_count > 0)
        //   - generate and save each sentence (SentenceDAO.insert)
        //   - show them in sentenceList and enable addToCorpusButton
    }

    @FXML
    private void onAddToCorpus() {
        // TODO: feed the sentences back through the importer. FileImporter currently takes a Path;
        //       either write them to a temp file, or add a constructor that takes text directly.
        //       Then SentenceDAO.markAddedToCorpus(ids).
    }
}
