package edu.sentencebuilder.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Tab;

/**
 * Controller for {@code main.fxml}: reloads the Words Report and Sentence History
 * tabs each time they are opened, so they show words and sentences added on other
 * tabs (imports, generated sentences) without pressing Refresh.
 */
public class MainController {

    @FXML private Tab wordsReportTab;
    @FXML private Tab sentenceHistoryTab;
    // Injected by fx:include: the include's fx:id plus "Controller".
    @FXML private WordsReportController wordsReportController;
    @FXML private SentenceHistoryController sentenceHistoryController;

    @FXML
    private void initialize() {
        refreshWhenSelected(wordsReportTab, wordsReportController::refresh);
        refreshWhenSelected(sentenceHistoryTab, sentenceHistoryController::refresh);
    }

    private static void refreshWhenSelected(Tab tab, Runnable refresh) {
        tab.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                refresh.run();
            }
        });
    }
}
