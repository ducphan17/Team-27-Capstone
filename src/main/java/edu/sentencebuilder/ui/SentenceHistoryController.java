package edu.sentencebuilder.ui;

import edu.sentencebuilder.dao.SentenceDAO;
import edu.sentencebuilder.model.GeneratedSentence;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sentence History tab: lists every generated sentence, newest first, and highlights
 * sentences that were generated more than once (CSS style class {@code duplicate}).
 */
public class SentenceHistoryController {

    private static final DateTimeFormatter CREATED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DUPLICATE_STYLE = "duplicate";

    @FXML private Button refreshButton;
    @FXML private Label statusLabel;
    @FXML private TableView<GeneratedSentence> sentenceTable;
    @FXML private TableColumn<GeneratedSentence, String> sentenceColumn;
    @FXML private TableColumn<GeneratedSentence, Integer> timesColumn;
    @FXML private TableColumn<GeneratedSentence, String> startWordColumn;
    @FXML private TableColumn<GeneratedSentence, String> algorithmColumn;
    @FXML private TableColumn<GeneratedSentence, String> createdColumn;

    private final SentenceDAO sentenceDAO = new SentenceDAO();
    /** How many times each sentence text occurs in the loaded history. */
    private Map<String, Integer> occurrences = Map.of();

    @FXML
    private void initialize() {
        sentenceColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getSentence()));
        timesColumn.setCellValueFactory(c ->
                new ReadOnlyObjectWrapper<>(occurrences.getOrDefault(c.getValue().getSentence(), 1)));
        startWordColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getStartWord()));
        algorithmColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getAlgorithm()));
        // yyyy-MM-dd HH:mm:ss sorts correctly as plain text.
        createdColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().getCreatedAt() == null ? "" : CREATED_FORMAT.format(c.getValue().getCreatedAt())));

        sentenceTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(GeneratedSentence item, boolean empty) {
                super.updateItem(item, empty);
                // Rows are recycled, so always remove the class before deciding to add it.
                getStyleClass().remove(DUPLICATE_STYLE);
                if (!empty && item != null && occurrences.getOrDefault(item.getSentence(), 1) > 1) {
                    getStyleClass().add(DUPLICATE_STYLE);
                }
            }
        });

        onRefresh();
    }

    @FXML
    private void onRefresh() {
        refreshButton.setDisable(true);
        statusLabel.setText("Loading sentences…");
        Background.run("load-sentences", sentenceDAO::findAll,
                sentences -> {
                    occurrences = countOccurrences(sentences);
                    sentenceTable.getItems().setAll(sentences);
                    sentenceTable.refresh(); // re-run row factories with the new counts
                    refreshButton.setDisable(false);
                    statusLabel.setText(summary(sentences, occurrences));
                },
                error -> {
                    refreshButton.setDisable(false);
                    statusLabel.setText(Background.describe(error));
                });
    }

    /** Counts how many times each exact sentence text appears. */
    static Map<String, Integer> countOccurrences(List<GeneratedSentence> sentences) {
        Map<String, Integer> counts = new HashMap<>();
        for (GeneratedSentence s : sentences) {
            counts.merge(s.getSentence(), 1, Integer::sum);
        }
        return counts;
    }

    /** e.g. "12 sentences, 2 generated more than once (highlighted)". */
    static String summary(List<GeneratedSentence> sentences, Map<String, Integer> occurrences) {
        long repeated = occurrences.values().stream().filter(n -> n > 1).count();
        String text = sentences.size() + (sentences.size() == 1 ? " sentence" : " sentences");
        return repeated == 0 ? text : text + ", " + repeated + " generated more than once (highlighted)";
    }
}
