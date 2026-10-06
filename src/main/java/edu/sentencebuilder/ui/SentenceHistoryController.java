package edu.sentencebuilder.ui;

import edu.sentencebuilder.model.GeneratedSentence;
import javafx.fxml.FXML;
import javafx.scene.control.TableView;

/**
 * Sentence History tab: lists every generated sentence and highlights duplicates.
 * PLACEHOLDER — layout only.
 */
public class SentenceHistoryController {

    @FXML private TableView<GeneratedSentence> sentenceTable;

    @FXML
    private void onRefresh() {
        // TODO: load SentenceDAO.findAll() in a Task; count occurrences of each sentence text;
        //   set sentenceTable.setRowFactory(...) returning a TableRow whose updateItem adds a CSS
        //   style class (e.g. "duplicate") when the count is > 1. Wire column cell value
        //   factories (give the columns fx:ids first).
    }
}
