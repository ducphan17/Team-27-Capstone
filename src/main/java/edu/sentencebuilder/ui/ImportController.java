package edu.sentencebuilder.ui;

import edu.sentencebuilder.dao.FileDAO;
import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.model.SourceFile;
import edu.sentencebuilder.parser.FileImporter;
import edu.sentencebuilder.parser.Tokenizer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Import tab: lets the user pick a .txt file and runs a {@link FileImporter}
 * on a background thread, with the progress bar and status label bound to the task.
 * Below it, an import history table lists every {@code source_file} row, newest first.
 */
public class ImportController {

    private static final DateTimeFormatter IMPORTED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML private Button chooseButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private TableView<SourceFile> fileTable;
    @FXML private TableColumn<SourceFile, String> filenameColumn;
    @FXML private TableColumn<SourceFile, Integer> wordCountColumn;
    @FXML private TableColumn<SourceFile, Integer> sentenceCountColumn;
    @FXML private TableColumn<SourceFile, String> importedColumn;
    @FXML private Label historyLabel;

    private final FileDAO fileDAO = new FileDAO();
    private boolean historyLoading;
    /** Set when a reload is requested mid-load, so the newest rows are not missed. */
    private boolean historyReloadPending;

    @FXML
    private void initialize() {
        filenameColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getFilename()));
        wordCountColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getWordCount()));
        sentenceCountColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getSentenceCount()));
        // yyyy-MM-dd HH:mm:ss sorts correctly as plain text.
        importedColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().getImportedAt() == null ? "" : IMPORTED_FORMAT.format(c.getValue().getImportedAt())));

        // Import is the first tab, so it is already selected at startup; MainController
        // reloads the history when the tab is opened again.
        refreshHistory();
    }

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
                wordDAO, new WordPairDAO(wordDAO), fileDAO);

        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());
        chooseButton.disableProperty().bind(task.runningProperty());

        // These handlers run on the FX thread, so touching controls here is safe.
        task.setOnSucceeded(e -> {
            unbind();
            refreshHistory();
        });
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

    /** Reloads the import history in the background; a request during a load runs once it finishes. */
    void refreshHistory() {
        if (historyLoading) {
            historyReloadPending = true;
            return;
        }
        historyLoading = true;
        historyLabel.setText("Loading import history…");
        Background.run("load-import-history", fileDAO::findAll,
                files -> {
                    fileTable.getItems().setAll(files);
                    historyLabel.setText(historySummary(files));
                    finishHistoryLoad();
                },
                error -> {
                    historyLabel.setText(Background.describe(error));
                    finishHistoryLoad();
                });
    }

    private void finishHistoryLoad() {
        historyLoading = false;
        if (historyReloadPending) {
            historyReloadPending = false;
            refreshHistory();
        }
    }

    /** e.g. "3 imports, 1250 words in total". */
    static String historySummary(List<SourceFile> files) {
        if (files.isEmpty()) {
            return "No imports yet.";
        }
        long words = files.stream().mapToLong(SourceFile::getWordCount).sum();
        return String.format("%d %s, %d %s in total",
                files.size(), files.size() == 1 ? "import" : "imports",
                words, words == 1 ? "word" : "words");
    }
}
