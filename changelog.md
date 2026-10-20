# Changelog

Current version: **1.5**

## Instructions for future updates

When a contributor asks to update this changelog:

- Add one new version entry above the previous entries, keeping the full history.
- Increase the version by one minor step for each new update: `1.1`, `1.2`, through `1.9`, then `2.0`, `2.1`, and so on. After any `.9`, increase the first number and reset the second number to `0`; do not use `1.10`.
- Update the current version shown at the top of this file.
- Record the contributor's full name under **Author** and the update date as `YYYY-MM-DD`. Ask for the name if it is unknown; do not reuse the previous author's name automatically. List all authors for a shared contribution.
- Review the actual changes since the previous checkpoint and describe what changed, why it matters, and how it was verified. Do not repeat earlier changes as new work.
- Distinguish implemented behavior from tested behavior. Record remaining verification when relevant.
- Use this project's version sequence even for small changes; it is a team convention rather than semantic versioning.

Entry template:

```markdown
## Version X.Y — YYYY-MM-DD

**Author:** Contributor's full name

### Changes

- Describe the changes and affected files.

### Verification

- Record checks performed and their results.
- Note relevant checks still pending.
```

## Version 1.5 — 2026-10-20

**Author:** Ben Quidlat

**Checkpoint:** Words Report and Sentence History tabs implemented on top of the v1.4 DAO methods.

### Changes

- **`ui/Background.java` (new):** Small helper that runs a DAO call in a `javafx.concurrent.Task` on a daemon thread and delivers success/failure callbacks on the FX thread. `describe(Throwable)` turns a failure into a readable message that includes the root cause (e.g. "Could not load words (db.properties not found ...)").
- **`WordsReportController.java` / `words_report.fxml`:** Loads `WordDAO.findAll()` on startup and on Refresh. Words sit in a `FilteredList` → `SortedList` bound to the table's sort order, so the filter and sort survive a refresh. Filter is a case-insensitive substring match. Count, Start count, and End count are editable (`TextFieldTableCell` with a lenient integer converter); edits are validated (no negatives, start/end ≤ total, whole numbers only) and saved with `WordDAO.update` in a background task. A failed save reverts the cell and shows the error. The Word column is read-only to avoid UNIQUE-key collisions. Added a status label (word count / "N of M match the filter" / save results).
- **`SentenceHistoryController.java` / `sentence_history.fxml`:** Loads `SentenceDAO.findAll()` (newest first) on startup and on Refresh. Columns wired with `fx:id`s; added a **Times** column showing how often each sentence was generated. A row factory adds the `duplicate` style class to sentences that occur more than once; status label summarizes the count.
- **`ui/app.css` (new):** `duplicate` row highlight, with selection colors preserved.
- **Tests (new):** `WordsReportControllerTest` (6) for filtering, count validation, and the integer converter; `SentenceHistoryControllerTest` (4) for duplicate counting and the summary text.
- **`README.md`:** Updated implemented / still-to-do lists.
- **`.gitignore`:** Added `samples/` so local test texts are not committed.

### Verification

- Main and test sources compile with `javac --release 21`; all 25 unit tests pass (Maven was not available on the dev machine, so tests were run with a reflection-based runner over the same classes).
- Headless JavaFX smoke test (no database configured): both FXML files and `main.fxml` load; load failures show the `db.properties` message and re-enable Refresh; default sort is alphabetical and column sort works; filter "CAT" shows `cat`, `concatenate`; invalid and non-numeric edits are rejected without saving; a valid edit whose save fails is reverted; duplicate rows receive the `duplicate` class and the Times column shows the right counts.
- Ran the app against a local MySQL 8.0.43 server with all six tabs visible. Imported a 2,058-word original sample text (`the-storm-keeper.txt`, not committed); the Import tab reported "Imported 2058 words in 190 sentences (529 distinct)". Checked in MySQL: 529 rows in `word` with `total_count` summing to 2,058, start and end counts each summing to 190, 1,351 rows in `word_pair`, and one `source_file` row.
- Words Report with that data: Refresh loads the words in alphabetical order, and spot-checked counts match MySQL (e.g. `his` total 48, start 21).
- Words Report filter narrows the table and updates the status line; a count edit shows "Saved" and the new value is still there after restarting the app and refreshing.
- **Pending:** check Sentence History duplicate highlighting with real rows (needs the Generate tab, or test rows inserted directly); run `mvn test`.

## Version 1.4 — 2026-10-20

**Author:** Paulo Nicart

**Checkpoint:** Completed remaining DAO persistence and query methods across `WordDAO`, `WordPairDAO`, `FileDAO`, and `SentenceDAO`.

### Changes

- **`WordDAO.java`:** Implemented the three remaining stubbed methods using `PreparedStatement` and `try-with-resources`, wrapping SQL failures in `DataAccessException`:
  - `findAll()`: Loads all rows from `word` ordered alphabetically (`ORDER BY text ASC`) and maps each row using the existing `mapRow()` helper for the Words Report tab.
  - `insertIfAbsent(String text)`: Inserts a new word with default zero counts if it does not already exist using MySQL 8 row-alias syntax (`INSERT INTO word (text) VALUES (?) AS incoming ON DUPLICATE KEY UPDATE text = word.text`), leaving existing word counts and `first_seen` timestamps untouched, then queries and returns its `word_id` for the Autocomplete tab.
  - `update(Word word)`: Updates `text`, `total_count`, `start_count`, and `end_count` for an existing word by `word_id` to support inline edits in the Words Report tab without deleting rows.
- **`WordPairDAO.java`:** Implemented `incrementUserCount(int fromWordId, int toWordId)`. Inserts a new pair with `count = 0` and `user_count = 1` or increments `user_count` by 1 on conflict using `AS incoming ON DUPLICATE KEY UPDATE user_count = word_pair.user_count + 1`, throwing `DataAccessException` on SQL failure.
- **`FileDAO.java`:** Implemented `findAll()` and a private `mapRow(ResultSet)` helper to query all records from `source_file` ordered newest first (`ORDER BY imported_at DESC, file_id DESC`), converting `imported_at` timestamps to `LocalDateTime`.
- **`SentenceDAO.java`:** Implemented the remaining read and batch-update methods:
  - `findAll()` and a private `mapRow(ResultSet)` helper: Queries all rows from `generated_sentence` ordered newest first with an ID tie-breaker (`ORDER BY created_at DESC, id DESC`) for the Sentence History tab.
  - `markAddedToCorpus(List<Integer> ids)`: Safely handles null or empty lists as a no-op, otherwise executes a batched `PreparedStatement` (`UPDATE generated_sentence SET added_to_corpus = TRUE WHERE id = ?`) for the Batch Generate tab.
- **`changelog.md`:** Recorded Version 1.4 changes and updated the current version header.

### Verification

- Ran `mvn clean compile` and `mvn test` to verify all modified DAO classes compile cleanly under Java 21 and all existing unit tests continue to pass.
- Pending verification: End-to-end MySQL query and mutation checks for these DAO methods once wired into the Words Report, Sentence History, Autocomplete, and Batch Generate UI controllers.

## Version 1.3 — 2026-10-19

**Author:** Arath Brosig

**Checkpoint:** Implemented MostFrequentGenerator loop avoidance

### Changes

- **`MostFrequentGenerator.java`:** Implemented the most-frequent algorithm logic. Added consecutive bigram loop-avoidance to prevent infinite recursion during sentence generation by checking candidate pairs against `sentenceSoFar`.
- **`changelog.md`:** Added this history, contributor attribution, an entry template, and the team's version-increment rules.

### Verification

- Added and ran `MostFrequentGeneratorTest.java` utilizing an in-memory `WordStatsSource` fake. Confirmed the loop avoidance logic successfully terminated an artificial infinte bigram loop ("hello"->"world"->"hello") before reaching the maximum word limit.
- Next task: Integrate generators with the real database (WordPairDAO) or wire them into the JavaFX UI controller. Pending tasks are not implemented in this checkpoint.

## Version 1.2 — 2026-10-17

**Author:** Aalvee Ahtav

**Checkpoint:** Manual verification of repeated imports and transaction rollback.

### Changes

- Completed the two pending manual import checks from v1.1. This checkpoint verifies existing persistence behavior; it adds no new application functionality.
- Temporarily added a filename-specific failure in `FileImporter.java` after saving words, pairs, and file metadata but before committing. Removed the failure block after the rollback check.
- Next task: implement `WordDAO.findAll()`, then connect a read-only Words Report for review in the application. These changes are not implemented in this checkpoint.

### Verification

- Imported `repeat-check.txt`, containing `Zorvex miplex. Zorvex talvex.`, twice through the application. MySQL queries confirmed exactly three matching word rows with unchanged IDs: `miplex = 6`, `talvex = 5`, and `zorvex = 7`.
- Confirmed total/start/end counts doubled on the second import: `miplex` and `talvex` each changed from `1/0/1` to `2/0/2`; `zorvex` changed from `2/2/0` to `4/4/0`.
- Confirmed exactly two matching word pairs, `zorvex → miplex` and `zorvex → talvex`, each with imported count `2` and `user_count = 0`. Confirmed two separate file records, each reporting 4 words and 2 sentences.
- Imported `rollback-check.txt`, containing `Zorvex miplex. Rollbackzor rollbackmip.`, with the temporary failure enabled. The application displayed `Import failed: Intentional rollback test`.
- Post-failure MySQL queries confirmed existing word counts stayed unchanged, neither new word persisted, and no `rollback-check.txt` file record remained. The contributor also confirmed the final pair-count check passed, with existing pair counts unchanged and no new test pair saved.
- These were manual application/MySQL checks using local test data. No automated integration tests were added and the unit test suite was not rerun for this checkpoint. Isolated test-database setup and documentation remain pending.

## Version 1.1 — 2026-10-15

**Author:** Aalvee Ahtav

**Checkpoint:** Basic text-file import persistence.

### Changes

- **`WordDAO.java`:** Implemented the previously stubbed `upsertCounts()` method. It now accepts the caller's JDBC connection and uses prepared statements with batch upserts to insert words or add to existing total, sentence-start, and sentence-end counts. It retrieves and returns word IDs for subsequent pair inserts, reports missing words as SQL errors, and preserves existing `first_seen` timestamps.
- **`WordPairDAO.java`:** Implemented the previously stubbed `upsertCounts()` method. It accepts the shared connection, converts word text to database IDs, checks for missing IDs, and batches inserts or count updates for word pairs. Imported counts do not change `user_count`.
- **`FileDAO.java`:** Changed `insert()` to accept the caller's connection instead of opening its own. It saves the filename, word count, and sentence count, retrieves the generated file ID, and leaves the shared connection open for the importer.
- **`FileImporter.java`:** Implemented `writeToDatabase()` and passed the sentence count into it. Imports now save words first, then pairs, then file metadata through one connection with automatic commits disabled. All records commit together after successful saves. SQL or runtime failures trigger rollback; rollback errors are attached to the original error. Removed the old separate file-record insert, replaced the obsolete TODO with explanatory comments, and retained the existing background-task processing and status reporting.
- **`README.md`:** Expanded the original setup instructions with repository cloning, macOS and Windows MySQL setup, dedicated database-user creation and password changes, platform-specific configuration-file copying, and clearer credential configuration examples.
- **`changelog.md`:** Added this history, contributor attribution, an entry template, and the team's version-increment rules.

### Verification

- Ran `mvn -o test` after connecting the importer: all 14 existing unit tests passed. These tests cover tokenization and weighted-random generation; they do not exercise MySQL persistence.
- Manually imported `Cat Sleeping.txt` containing `The cat sleeps. The cat eats.` through the application. The interface reported 6 words, 2 sentences, and 4 distinct words.
- Verified the saved word statistics in MySQL: `the` has total/start/end counts of `2/2/0`; `cat` has `2/0/0`; `sleeps` and `eats` each have `1/0/1`.
- Verified the saved pair counts: `the → cat = 2`, `cat → sleeps = 1`, and `cat → eats = 1`.
- Verified the imported-file record: `Cat Sleeping.txt`, 6 words, 2 sentences.
- Repeated-import behavior and rollback on a failed import remain to be tested. Other unfinished DAO methods and application tabs remain outside this checkpoint.
