package edu.sentencebuilder.ui;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;

import java.util.Locale;

/**
 * Controller for {@code main.fxml}: reloads the Import history, Words Report, and
 * Sentence History each time their tab is opened, so they show files, words, and
 * sentences added on other tabs (imports, corpus additions, generated sentences)
 * without pressing Refresh.
 */
public class MainController {

    private static final PseudoClass DARK = PseudoClass.getPseudoClass("dark");

    @FXML private VBox workspace;
    @FXML private ToggleButton darkModeToggle;
    @FXML private Tab importTab;
    @FXML private Tab wordsReportTab;
    @FXML private Tab sentenceHistoryTab;
    // Injected by fx:include: the include's fx:id plus "Controller".
    @FXML private ImportController fileImportController;
    @FXML private WordsReportController wordsReportController;
    @FXML private SentenceHistoryController sentenceHistoryController;

    @FXML
    private void initialize() {
        refreshWhenSelected(importTab, fileImportController::refreshHistory);
        refreshWhenSelected(wordsReportTab, wordsReportController::refresh);
        refreshWhenSelected(sentenceHistoryTab, sentenceHistoryController::refresh);
    }

    @FXML
    private void onToggleDarkMode() {
        workspace.pseudoClassStateChanged(DARK, darkModeToggle.isSelected());
        darkModeToggle.setText(darkModeToggle.isSelected() ? "Light mode" : "Dark mode");
        darkModeToggle.setAccessibleText("Switch to " + darkModeToggle.getText().toLowerCase(Locale.ROOT));
    }

    private static void refreshWhenSelected(Tab tab, Runnable refresh) {
        tab.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                refresh.run();
            }
        });
    }
}
