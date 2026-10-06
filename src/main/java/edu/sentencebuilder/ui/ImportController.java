package edu.sentencebuilder.ui;

import edu.sentencebuilder.dao.FileDAO;
import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.parser.FileImporter;
import edu.sentencebuilder.parser.Tokenizer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.FileChooser;

import java.io.File;

/**
 * Import tab: lets the user pick a .txt file and runs a {@link FileImporter}
 * on a background thread, with the progress bar and status label bound to the task.
 */
public class ImportController {

    @FXML private Button chooseButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;

    @FXML
    private void onChooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a text file");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text files", "*.txt"));
        File file = chooser.showOpenDialog(chooseButton.getScene().getWindow());
        if (file == null) {
            return;
        }

        WordDAO wordDAO = new WordDAO();
        FileImporter task = new FileImporter(file.toPath(), new Tokenizer(),
                wordDAO, new WordPairDAO(wordDAO), new FileDAO());

        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());
        chooseButton.disableProperty().bind(task.runningProperty());

        // These handlers run on the FX thread, so touching controls here is safe.
        task.setOnSucceeded(e -> unbind());
        task.setOnFailed(e -> {
            unbind();
            Throwable error = task.getException();
            statusLabel.setText("Import failed: " + (error == null ? "unknown error" : error.getMessage()));
        });

        Thread worker = new Thread(task, "file-import");
        worker.setDaemon(true); // don't keep the JVM alive after the window closes
        worker.start();
    }

    private void unbind() {
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();
        chooseButton.disableProperty().unbind();
        chooseButton.setDisable(false);
    }
}
