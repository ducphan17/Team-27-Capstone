package edu.sentencebuilder.ui;

import edu.sentencebuilder.model.Word;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/**
 * Words Report tab: sortable, filterable table of every word, with in-place editing.
 * Deleting words is intentionally not supported.
 * PLACEHOLDER — columns are wired, data loading is not.
 */
public class WordsReportController {

    @FXML private TextField filterField;
    @FXML private TableView<Word> wordTable;
    @FXML private TableColumn<Word, String> textColumn;
    @FXML private TableColumn<Word, Integer> totalColumn;
    @FXML private TableColumn<Word, Integer> startColumn;
    @FXML private TableColumn<Word, Integer> endColumn;

    @FXML
    private void initialize() {
        // Lambdas instead of PropertyValueFactory: no reflection, and typos fail at compile time.
        textColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getText()));
        totalColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getTotalCount()));
        startColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getStartCount()));
        endColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getEndCount()));
        wordTable.getSortOrder().add(textColumn); // alphabetical by default
    }

    @FXML
    private void onRefresh() {
        // TODO: load WordDAO.findAll() in a Task, then on success wrap it as
        //   FilteredList<Word> filtered = new FilteredList<>(FXCollections.observableArrayList(words));
        //   filterField.textProperty() listener -> filtered.setPredicate(w -> w.getText().contains(text));
        //   SortedList<Word> sorted = new SortedList<>(filtered);
        //   sorted.comparatorProperty().bind(wordTable.comparatorProperty());
        //   wordTable.setItems(sorted);
        // TODO: editing — use TextFieldTableCell with an IntegerStringConverter, and in
        //   setOnEditCommit update the Word and call WordDAO.update(word) in a Task.
    }
}
