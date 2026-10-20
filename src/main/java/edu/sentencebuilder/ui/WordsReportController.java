package edu.sentencebuilder.ui;

import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.model.Word;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.StringConverter;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Words Report tab: sortable, filterable table of every word, with in-place
 * editing of the three counts. Word text is read-only (renaming could collide with
 * the UNIQUE key), and deleting words is intentionally not supported.
 */
public class WordsReportController {

    @FXML private TextField filterField;
    @FXML private Button refreshButton;
    @FXML private Label statusLabel;
    @FXML private TableView<Word> wordTable;
    @FXML private TableColumn<Word, String> textColumn;
    @FXML private TableColumn<Word, Integer> totalColumn;
    @FXML private TableColumn<Word, Integer> startColumn;
    @FXML private TableColumn<Word, Integer> endColumn;

    private final WordDAO wordDAO = new WordDAO();
    /** Every loaded word; refreshes replace its contents so the filter and sort stay in place. */
    private final ObservableList<Word> allWords = FXCollections.observableArrayList();
    private FilteredList<Word> filteredWords;
    /** IDs of words with a save in progress. Only touched on the FX thread. */
    private final Set<Integer> savingWordIds = new HashSet<>();

    @FXML
    private void initialize() {
        // Lambdas instead of PropertyValueFactory: no reflection, and typos fail at compile time.
        textColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getText()));
        textColumn.setEditable(false);
        setUpCountColumn(totalColumn, Word::getTotalCount, Word::setTotalCount);
        setUpCountColumn(startColumn, Word::getStartCount, Word::setStartCount);
        setUpCountColumn(endColumn, Word::getEndCount, Word::setEndCount);

        filteredWords = new FilteredList<>(allWords);
        filterField.textProperty().addListener((obs, old, text) -> {
            filteredWords.setPredicate(w -> matchesFilter(w, text));
            updateStatus();
        });
        SortedList<Word> sortedWords = new SortedList<>(filteredWords);
        sortedWords.comparatorProperty().bind(wordTable.comparatorProperty());
        wordTable.setItems(sortedWords);
        wordTable.getSortOrder().add(textColumn); // alphabetical by default
        // No load here: MainController calls refresh() whenever this tab is opened.
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    /** Reloads all words in the background; does nothing if a load is already running. */
    void refresh() {
        if (refreshButton.isDisabled()) {
            return;
        }
        refreshButton.setDisable(true);
        statusLabel.setText("Loading words…");
        Background.run("load-words", wordDAO::findAll,
                words -> {
                    allWords.setAll(words);
                    refreshButton.setDisable(false);
                    updateStatus();
                },
                error -> {
                    refreshButton.setDisable(false);
                    statusLabel.setText(Background.describe(error));
                });
    }

    /** Makes a count column editable; a commit validates, updates the Word, and saves it. */
    private void setUpCountColumn(TableColumn<Word, Integer> column,
                                  Function<Word, Integer> getter, BiConsumer<Word, Integer> setter) {
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(getter.apply(c.getValue())));
        column.setCellFactory(TextFieldTableCell.forTableColumn(new LenientIntegerConverter()));
        column.setOnEditCommit(event -> {
            Word word = event.getRowValue();
            Integer oldValue = event.getOldValue();
            Integer newValue = event.getNewValue();

            // One save per word at a time, so an older save can't finish last
            // (or fail and undo) after a newer edit.
            if (savingWordIds.contains(word.getWordId())) {
                wordTable.refresh();
                statusLabel.setText("Still saving \"" + word.getText() + "\"; edit it again when the save finishes.");
                return;
            }

            String problem;
            if (newValue == null) {
                problem = "Counts must be whole numbers.";
            } else {
                setter.accept(word, newValue);
                problem = validateCounts(word.getTotalCount(), word.getStartCount(), word.getEndCount());
            }
            if (problem != null) {
                setter.accept(word, oldValue);
                wordTable.refresh();
                statusLabel.setText("Not saved: " + problem);
                return;
            }
            save(word, () -> setter.accept(word, oldValue));
        });
    }

    private void save(Word word, Runnable undo) {
        wordTable.refresh();
        wordTable.sort(); // the edited column may be the sort key
        statusLabel.setText("Saving \"" + word.getText() + "\"…");
        int wordId = word.getWordId();
        savingWordIds.add(wordId);
        // The background thread gets a snapshot, never the Word the table is editing.
        Word snapshot = new Word(wordId, word.getText(), word.getTotalCount(),
                word.getStartCount(), word.getEndCount(), word.getFirstSeen());
        Background.run("update-word", () -> {
                    wordDAO.update(snapshot);
                    return null;
                },
                ignored -> {
                    savingWordIds.remove(wordId);
                    statusLabel.setText("Saved \"" + word.getText() + "\".");
                },
                error -> {
                    savingWordIds.remove(wordId);
                    undo.run();
                    wordTable.refresh();
                    wordTable.sort();
                    statusLabel.setText("Could not save \"" + word.getText() + "\": " + Background.describe(error));
                });
    }

    private void updateStatus() {
        int shown = filteredWords.size();
        int total = allWords.size();
        statusLabel.setText(shown == total
                ? total + " words"
                : shown + " of " + total + " words match the filter");
    }

    /** True if the word contains the filter text (case-insensitive); a blank filter matches everything. */
    static boolean matchesFilter(Word word, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return word.getText().toLowerCase().contains(filter.trim().toLowerCase());
    }

    /**
     * Checks edited counts. A word cannot start or end more sentences than it appears in.
     *
     * @return a message describing the problem, or {@code null} if the counts are valid
     */
    static String validateCounts(int total, int start, int end) {
        if (total < 0 || start < 0 || end < 0) {
            return "Counts cannot be negative.";
        }
        if (start > total) {
            return "Start count cannot exceed the total count.";
        }
        if (end > total) {
            return "End count cannot exceed the total count.";
        }
        return null;
    }

    /**
     * Like IntegerStringConverter, but returns {@code null} for text that is not a whole
     * number instead of throwing, so a typo is rejected with a message rather than an exception.
     */
    static final class LenientIntegerConverter extends StringConverter<Integer> {
        @Override
        public String toString(Integer value) {
            return value == null ? "" : value.toString();
        }

        @Override
        public Integer fromString(String text) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException | NullPointerException e) {
                return null;
            }
        }
    }
}
