package edu.sentencebuilder.ui;

import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

/**
 * Autocomplete tab: suggests the top followers of the last completed word and
 * records the user's choices in {@code word_pair.user_count}.
 * PLACEHOLDER — layout only.
 */
public class AutocompleteController {

    @FXML private TextField inputField;
    @FXML private ListView<String> suggestionList;

    @FXML
    private void initialize() {
        // TODO:
        //  - listen to inputField.textProperty(); when the new text ends with ' ', ',' or '.',
        //    take the last completed word and load WordPairDAO.getFollowers(word) in a Task
        //    (keep only the top ~5); show them in suggestionList.
        //  - clicking a suggestion appends it to inputField.
        //  - when a word is completed (chosen or typed): if it's unknown, WordDAO.insertIfAbsent;
        //    then WordPairDAO.incrementUserCount(previousWordId, wordId).
        //  - typing fast can start several Tasks; cancel the previous one or ignore stale results.
    }
}
