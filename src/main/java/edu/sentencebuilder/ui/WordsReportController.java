package edu.sentencebuilder.ui;

import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;
import edu.sentencebuilder.generator.Follower;
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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.StringConverter;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    @FXML private Label followersTitle;
    @FXML private Label followersStatus;
    @FXML private Button followersRefreshButton;
    @FXML private TableView<FollowerDetails> followersTable;
    @FXML private TableColumn<FollowerDetails, String> followerColumn;
    @FXML private TableColumn<FollowerDetails, Integer> importedColumn;
    @FXML private TableColumn<FollowerDetails, Integer> userColumn;
    @FXML private TableColumn<FollowerDetails, Long> weightColumn;
    @FXML private TableColumn<FollowerDetails, Double> probabilityColumn;

    private final WordDAO wordDAO = new WordDAO();
    private final WordPairDAO wordPairDAO = new WordPairDAO(wordDAO);
    /** Invalidates results from older selections, including a selection cleared by filtering. */
    private long followersRequest;
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
        followerColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().text()));
        importedColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().importedCount()));
        userColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().userCount()));
        weightColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().weight()));
        probabilityColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().probability()));
        probabilityColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double probability, boolean empty) {
                super.updateItem(probability, empty);
                setText(empty || probability == null ? null : String.format(Locale.ROOT, "%.1f%%", probability * 100));
            }
        });
        weightColumn.setSortType(TableColumn.SortType.DESCENDING);
        followersTable.getSortOrder().add(weightColumn);
        followersTable.setPlaceholder(new Label("Select a word to inspect its followers."));
        wordTable.getSelectionModel().selectedItemProperty().addListener((obs, old, word) -> onInspectFollowers());
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
                    Word selected = wordTable.getSelectionModel().getSelectedItem();
                    wordTable.getSelectionModel().clearSelection();
                    allWords.setAll(words);
                    if (selected != null) {
                        wordTable.getItems().stream().filter(w -> w.getWordId() == selected.getWordId())
                                .findFirst().ifPresent(wordTable.getSelectionModel()::select);
                    }
                    refreshButton.setDisable(false);
                    updateStatus();
                },
                error -> {
                    refreshButton.setDisable(false);
                    statusLabel.setText(Background.describe(error));
                });
    }

    /** Reloads the selected word's followers off the FX thread; stale replies never replace newer ones. */
    @FXML
    private void onInspectFollowers() {
        long request = ++followersRequest;
        Word word = wordTable.getSelectionModel().getSelectedItem();
        followersTable.getItems().clear();
        followersRefreshButton.setDisable(word == null);
        if (word == null) {
            followersTitle.setText("Word-pair inspector");
            followersStatus.setText("Select a word above to see what can follow it.");
            followersTable.setPlaceholder(new Label("Select a word to inspect its followers."));
            return;
        }
        String text = word.getText();
        followersTitle.setText("Words following “" + text + "”");
        followersStatus.setText("Loading followers…");
        followersTable.setPlaceholder(new Label("Loading…"));
        Background.run("load-followers", () -> followerDetails(wordPairDAO.getFollowers(text)),
                rows -> {
                    if (request != followersRequest) return;
                    followersTable.getItems().setAll(rows);
                    followersTable.sort();
                    followersTable.setPlaceholder(new Label("No followers recorded for this word."));
                    followersStatus.setText(rows.isEmpty() ? "No followers recorded for “" + text + "”."
                            : rows.size() + (rows.size() == 1 ? " follower" : " followers"));
                },
                error -> {
                    if (request != followersRequest) return;
                    followersTable.setPlaceholder(new Label("Could not load followers. Press Refresh followers to retry."));
                    followersStatus.setText(Background.describe(error));
                });
    }

    /** Conditional next-word probabilities; sentence-ending decisions are handled separately by generators. */
    static List<FollowerDetails> followerDetails(List<Follower> followers) {
        long totalWeight = followers.stream().mapToLong(f -> (long) f.count() + f.userCount()).sum();
        return followers.stream().map(f -> {
            long weight = (long) f.count() + f.userCount();
            return new FollowerDetails(f.text(), f.count(), f.userCount(), weight,
                    totalWeight == 0 ? 0 : (double) weight / totalWeight);
        }).toList();
    }

    /** Read-only row for the inspector; long arithmetic avoids overflow when adding stored counts. */
    record FollowerDetails(String text, int importedCount, int userCount, long weight, double probability) {}

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
