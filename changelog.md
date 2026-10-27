# Changelog

Current version: **1.9**

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

## Version 1.9 — 2026-10-26

**Author:** Tien Phan

**Checkpoint:** Autocomplete tab.

### Changes

- **`AutocompleteController.java` / `autocomplete.fxml`:** Replaced the placeholder. Typing a space, comma, or period after a word shows that word's top 5 followers, ordered by `count + user_count`, each with its imported and typed counts (for example `cat (imported 3 · typed 2)`), so `user_count` is visible in the app. Lookups run in the background; a counter discards results from older lookups so fast typing never shows stale suggestions. Clicking a suggestion or pressing Enter on it replaces any partly typed word and adds a space. Each sentence is saved only when it ends with `.`, `!`, or `?`, or when Enter is pressed in the text field (which also clears it), using the text in the field at that moment. Typos corrected before then are never stored, and picked words are counted once. Saves run in typing order on one background thread; a status label shows what was saved or why a save failed.
- **`AutocompleteInput.java` (new):** JavaFX-free text rules: when an edit finishes a word (for suggestions), when it ends a sentence, which sentences have not been saved yet, and how a suggestion is inserted. Words are normalized with the importer's `Tokenizer`. Backspacing over a period and typing it again saves nothing new; if already-saved text is edited, only the last sentence is saved, so no sentence is counted twice.
- **`AutocompleteService.java` (new):** `recordSentences` saves finished sentences in a single transaction: `insertIfAbsent` adds unknown words with zero counts, then `incrementUserCount` adds 1 to `user_count` for each pair of neighboring words. Pairs continue across commas but never cross a sentence end, matching imported pairs. Imported total/start/end and pair counts are never changed. Validates every word's length before opening a connection and rolls back on SQL or runtime errors. Takes an injectable connection factory like `CorpusService`.
- **`WordDAO.java` / `WordPairDAO.java`:** Added `insertIfAbsent(Connection, String)` and `incrementUserCount(Connection, int, int)` overloads that use the caller's connection and leave it open. The existing methods delegate to them. Fixed the indentation of `insertIfAbsent`.
- **Unit tests:** Added `AutocompleteInputTest` (12 tests) and `AutocompleteServiceValidationTest` (2 tests). The suite now contains 66 unit tests, up from 52.
- **`FileImporterIT.java`:** Added 2 MySQL integration tests through the production `AutocompleteService`: unknown words are added with zero counts, `user_count` increases for new and existing pairs, imported words and pair counts are unchanged, and a sentence-start word creates no pair; an injected failure after the new word is inserted rolls it back. The suite now contains 13 integration tests.
- **PR follow-up:** Changed saving from once per finished word to once per finished sentence, so corrected typos are not stored permanently (words are never deleted). Showed imported and typed counts in the suggestion list. Removed an unused import from the controller. After a sentence ends, the tab now says "Sentence finished" (the status label confirms the save once it completes) or, if nothing new needed saving (for example, retyping a deleted period), "This sentence was already saved"; it previously always said "Sentence saved".
- **`README.md`:** Documented the Autocomplete tab, updated test counts, integration checks, verification status, and project layout, and removed Autocomplete from the remaining work.

### Verification

- Ran `mvn -Pdb-tests verify` against the isolated test database: all 66 unit tests and 13 MySQL integration tests passed, with zero failures, errors, or skipped tests.
- Ran `mvn -Pdb-tests verify` again after the PR follow-up: all 66 unit tests and 13 MySQL integration tests passed.
- Manually checked the Autocomplete tab in the running app against MySQL after importing `Puppies chase balls. Puppies chase cats. Puppies love balls.`:
  - Suggestions appeared after a space and a comma with imported/typed counts, ordered by weight (`chase (imported 2 · typed 0)` above `love (imported 1 · typed 0)`), and the typed count increased after each saved sentence.
  - Sentences saved on `.` and on Enter (which cleared the field); picked suggestions replaced the partly typed word and were counted once.
  - A corrected typo (`chsae`) was never stored, and clearing the field saved nothing.
  - Retyping a deleted period showed "This sentence was already saved" and did not save twice (`love→balls` `user_count` stayed 1). This check found the earlier always-"Sentence saved" message, which was fixed and rechecked.
  - SQL confirmed the expected pairs: `puppies→chase` 2/3, `puppies→love` 1/3, `chase→balls` 1/2, `chase→cats` 1/1, `love→balls` 1/1, and the new typed-only pairs `love→cats` 0/1 and `love→frisbees` 0/1. Imported word counts were unchanged (`puppies` 3/3/0, `chase` 2/0/0, `love` 1/0/0, `balls` 2/0/2, `cats` 1/0/1), `frisbees` was added with 0/0/0, and Words Report showed `frisbees` and no `chsae`.
- The optional error-handling checks (database stopped, 101-character word) were not part of this manual pass.

## Version 1.8 — 2026-10-25

**Author:** Aalvee Ahtav

**Checkpoint:** Batch Generate, saving generated results, adding selected sentences to the corpus, and typed word-count records.

### Changes

- **`WordDAO.java`:** Added `findStartingWords()` to load words with `start_count > 0`, ordered by text. Added a connection-taking overload so isolated integration tests exercise the same query without application credentials; the existing application-facing method opens and closes its own connection.
- **`StartingWordPicker.java` (new):** Chooses starting words randomly, weighted by `start_count`. Zero-count words receive no tickets, an empty eligible set raises a readable import-first error, and a `long` total supports combined weights above `Integer.MAX_VALUE`.
- **`BatchGenerateController.java`:** Replaced the placeholder with background generation and saving for 1–1000 sentences using Weighted random or Most frequent. Both algorithms use weighted random starting words and the generator's default 20-word limit. One autocommit connection is reused for sentence inserts, preserving partial saves without opening a new save connection per sentence. Empty starting-word results raise an import-first error through the task failure handler before the generation loop. Each sentence is saved with its start word and algorithm; list items retain the saved model and database ID. Successfully saved results remain visible with a partial-success message if a later generation or insert fails.
- **Batch controls and selection:** Validates the editable count spinner's text directly, uses a lenient converter, and disables controls while work runs. Supports multiple selection and enables **Add selected to corpus** only when the selection contains a sentence not already marked as added in the current list. Successful additions show `[Added to corpus]`; later additions skip those rows. Different generated rows with identical sentence text remain separate results.
- **`batch_generate.fxml`:** Added the Generate button ID and status label required by the controller, and renamed the corpus button to **Add selected to corpus**.
- **`FileImporter.java` / `TextImporter.java`:** Extracted JavaFX-independent counting, validation, text import, and shared `saveCounts()` persistence into `TextImporter`. The file task delegates persistence and its public `importText(Connection, String, String)` entry point to this shared importer; corpus feedback uses it directly without constructing a task or placeholder path. One `Map<String, WordCounts>` groups mutable total/start/end counters, and `toWordCounts()` produces immutable snapshots for persistence. Distinct-word reporting and validation use the combined map. Text imports reject wordless input with `InvalidImportException`, retain v1.7's word-length checks, and leave transaction ownership with the caller. Ordinary file imports retain their transaction and validation before the save-phase message. Comments explain lookups, counters, snapshots, and single-word sentence boundaries.
- **`CorpusService.java` (new):** Moved the complete Add selected transaction out of the controller into a non-UI service with an injectable connection factory. Both the controller and integration tests call `addToCorpus`. Selected sentence text is imported with source name `batch-generated`. Counts, pair statistics, the source record, and `added_to_corpus` flags commit together on one connection; SQL/runtime errors roll back the transaction. Visible flags change only after commit succeeds. Corpus additions increase the existing word statistics shown in Words Report; they do not create a separate added-words list.
- **`SentenceDAO.java`:** Added `markAddedToCorpus(Connection, List<Integer>)` for the shared import transaction and `insert(Connection, GeneratedSentence)` for isolated persistence checks. Both leave the supplied connection open and do not commit. Existing convenience methods delegate through their own connections.
- **Unit tests:** Added `StartingWordPickerTest` (4 tests) for exact ticket boundaries at weights 3/1, zero-count exclusion, empty input, and totals above the integer range; added `BatchGenerateControllerTest` (2 tests) for valid/invalid count text. Extended `FileImporterValidationTest` with 2 tests for oversized and wordless text rejected before using a connection. Together with the 2 word-count tests below, the suite now contains 52 unit tests, up from 42 in v1.7.
- **`FileImporterIT.java`:** Added 6 MySQL integration tests, bringing the suite to 11. The commit and rollback tests call the production `CorpusService` with injected DAO failures instead of copying the controller transaction. New coverage checks eligible starting words, generated IDs and saved sentence fields for both algorithm labels, text-import counts/pairs/source metadata, atomic count-and-flag commit, rollback after text import, and rollback after flags are marked. Fresh connections verify committed state and complete rollback snapshots; unselected sentences remain unmarked. All tests retain the dedicated test-user/database guards and never use application settings.
- Added code comments explaining weighted tickets, connection ownership, selection snapshots, partial batch results, and transaction boundaries.
- **`ImportedWordCounts.java` (new) / `WordDAO.java`:** Added an immutable record containing word text and total/start/end counts. `upsertCounts` now accepts `List<ImportedWordCounts>` instead of three separate maps, keeping each word's related data together in a typed DAO input. Full-batch word-length validation still runs before database writes, and the returned word-to-ID map remains a lookup for resolving pairs.
- **Word-count tests:** Updated `WordDAOValidationTest` for the object-based input and added 2 `FileImporterStatsTest` cases covering aggregated totals, zero boundary counts, empty input, repeated words, single-word sentences, and snapshots remaining unchanged after further counting. The unit suite now contains 52 tests.

- **`README.md`:** Updated implemented features, the 52-test unit breakdown, all 11 integration checks, shared typed counting, project layout, and verified DAO coverage. Removed completed Batch Generate work from the remaining-work table and made Autocomplete the next checkpoint.

### Verification

- **PR review verification:** Ran `mvn -Pdb-tests verify` after the word-count refactor and service extraction: all 52 unit tests and 11 MySQL integration tests passed, with zero failures, errors, or skipped tests. The integration suite uses the guarded disposable test database and exercises the actual corpus service's commit, SQL-failure rollback, and runtime-failure rollback paths.
- **Running application:** Contributor screenshots show 10 generated and saved sentences for each algorithm. Sentence History displays the batch results with their algorithm labels, newest-first ordering, duplicate counts, and highlighted repeated sentences. The Add selected button is disabled when nothing is selected.
- **Corpus addition walkthrough:** After following the selection/addition and Words Report walkthrough, the contributor confirmed it works. The walkthrough checks the success message and `[Added to corpus]` marker, then verifies that adding one `Zorvex talvex.` result increases `zorvex` total/start counts and `talvex` total/end counts by one.
- `git diff --check`: passes.
- **Remaining manual checks:** Invalid count input, database failure messages/control recovery, partial generation failure, and preventing another addition of an already-marked result have not been explicitly confirmed in the running app. Automated coverage tests count parsing and database transactions, but does not drive these JavaFX interactions.
- **Next checkpoint:** Autocomplete. Tokenizer edge cases and DAO methods outside this checkpoint remain separate work.

## Version 1.7 — 2026-10-20

**Author:** Aalvee Ahtav

**Updated:** 2026-10-21 — Automated import verification and word-length handling.

### Changes

- **`WordPairDAO.java`:** Changed `upsertCounts` to accept a `List<WordPair>` instead of nested word-count maps and a word-ID map. The batch insert now reads IDs and counts directly from the existing model objects, giving the DAO a typed record interface.
- **`FileImporter.java`:** Added `Stats.toWordPairs` to resolve database IDs and convert aggregated counts into `WordPair` objects after saving words. Missing IDs still raise `SQLException` within the import transaction. Maps remain internal lookup tables for counting and ID resolution.
- Added code comments explaining the conversion, the counting lookup table, and the DAO batch loop. Imported counts continue to leave database `user_count` values unchanged.
- **`FileImporter.java`:** Added a 100-character limit for normalized words, measured in Unicode code points to match `word.text VARCHAR(100)`. The importer validates every distinct word before opening a database connection and rejects the entire import with a shortened word preview and an explicit "No data was imported" message. It never truncates oversized words.
- Added a package-private connection factory constructor and package access to `writeToDatabase` so tests can exercise the same validation and transaction code as the UI without starting JavaFX. The existing public constructor continues to use `Database.getConnection`. Comments explain character counting, connection ownership, and rollback.
- **`FileImporterStatsTest.java` (5 tests):** Covers resolved pair IDs and aggregated counts, multiple destinations, zero imported `userCount`, empty statistics, single-word sentences, and missing source/destination IDs.
- **`FileImporterValidationTest.java` (3 tests):** Covers acceptance at 100 characters, supplementary Unicode character counting at 100/101 characters, and rejection of a mixed valid/oversized import before a connection opens.
- **`FileImporterIT.java` (5 tests):** Adds real MySQL checks for repeated imports, SQL/runtime rollback, successful 100-character imports, and unchanged stored rows after oversized input. Repeated imports preserve word IDs, `first_seen`, and an existing `user_count = 7`, while doubling imported counts and creating separate file records. Rollback failures are injected only through a test `FileDAO`, after all writes and before commit; fresh connections compare all four tables, including IDs and timestamps.
- **Isolated MySQL fixtures:** Added `db-test.properties.example`, ignored local `db-test.properties`, and table-only `src/test/resources/schema-test.sql`. Tests require the dedicated `sentencebuilder_test` username and check the selected database on every connection before schema creation, writes, or cleanup. Test tables are cleared before and after each test; tests never fall back to application settings.
- **`pom.xml`:** Added the opt-in `db-tests` profile using Maven Failsafe. `mvn test` remains database-free; `mvn -Pdb-tests verify` adds integration tests and fails when test configuration or MySQL is unavailable.
- **`README.md`:** Added step-by-step test database/user setup, credential configuration, test commands, cleanup behavior, report locations, and the running-app oversized-word check; updated implemented behavior and automated-test coverage.

- **PR review follow-up (2026-10-21):** `FileImporter.call()` now validates immediately after counting, before displaying "Saving to database..."; validation remains at the `writeToDatabase` entry point as well.
- Moved the shared 100-character limit and Unicode-aware check to `Word.MAX_WORD_LENGTH` / `Word.validateTextLength`. Direct `WordDAO.insertIfAbsent`, batch `upsertCounts`, and `update` calls now reject oversized text before preparing writes, covering future Autocomplete usage too.
- Added `InvalidImportException` to distinguish invalid import input from SQL failures and unrelated runtime errors while preserving the clear no-data-imported message and original validation cause.
- Integration tests now drop and recreate the four guarded test tables in `@BeforeAll`, applying changed fixture definitions instead of silently retaining stale tables. The README explains this lifecycle and the need to keep the test schema aligned with the application schema.
- Added `WordTest` and `WordDAOValidationTest` (6 unit tests) for shared boundary/Unicode rules and rejection at direct DAO write entry points; updated importer tests to require `InvalidImportException`.
- Corrected the contributor marker in `pom.xml` from Java-style text to an XML comment so Maven can parse the build file.

### Verification

- **PR review follow-up:** `mvn test` passes all 42 unit tests, and `mvn -Pdb-tests verify` passes all 42 unit tests plus 5 MySQL integration tests, with no failures, errors, or skipped tests. The updated integration run rebuilds the isolated test tables before testing imports. `git diff --check` passes.
- **Repository Git identity:** Set the local commit name/email to the contributor's confirmed values and verified with `git var GIT_AUTHOR_IDENT`. This applies to future commits; the existing PR commit has not been amended or rewritten.
- **Original refactor baseline:** `mvn clean test` compiled main/test sources under Java 21 and passed the 28 pre-existing unit tests.
- **Automated import checkpoint:** Maven test reports show all 36 unit tests passing (including the 8 new import tests) and all 5 MySQL integration tests passing, with no failures, errors, or skipped tests. The integration suite was verified against isolated MySQL 8.4.11; the contributor subsequently configured the local test database and ran the checkpoint.
- **Contributor walkthrough completed (2026-10-21):** Aalvee Ahtav confirmed reviewing the conversion tests and import validation, running `mvn -Dtest=FileImporterStatsTest test`, creating the isolated MySQL database/user, configuring the ignored `db-test.properties`, running `mvn clean test`, `mvn -Pdb-tests verify`, and `git diff --check`, and launching the app with `mvn javafx:run` to verify the oversized-word error. This completes all six documented setup and verification steps locally.
- **Running-app oversized-word check (2026-10-21):** The contributor supplied a screenshot showing rejection of a 101-character word with the 100-character-limit message, a shortened preview, and "No data was imported." The Choose file button is enabled again. Unchanged database contents are verified separately by the integration test snapshots; the screenshot confirms the visible error handling.
- `git diff --check`: passes.
- **Manual MySQL import verification after the refactor:** Started with empty application tables and reset ID counters. Imported `repeat-check.txt` containing `Zorvex miplex. Zorvex talvex.` twice. The first import saved three words and two pairs; after the second, word IDs remained `miplex = 2`, `talvex = 1`, and `zorvex = 3`. Total/start/end counts doubled to `2/0/2`, `2/0/2`, and `4/4/0`, respectively. Both pairs (`zorvex → miplex` and `zorvex → talvex`) had count `2` and `user_count = 0`, with no duplicate rows. Two separate `source_file` records each reported 4 words and 2 sentences.
- **Manual rollback verification after the refactor:** Used `rollback-check.txt` containing `Zorvex miplex. Rollbackzor rollbackmip.` and a temporary filename-specific `SQLException` after `fileDAO.insert` but before commit. Post-failure MySQL queries showed the original three words and two pairs with unchanged counts, no new test words or pairs, and only the two successful import records. The temporary failure block was removed afterward.
- **Words Report regression review:** The contributor confirmed automatic loading, case-insensitive filtering (`ZOR`), column sorting, and editing worked. Changed `miplex` total count from 2 to 3; the value survived Refresh and an application restart. Negative (`-1`), nonnumeric (`abc`), and total-below-end-count (`1`) edits were rejected. Restored the total to 2; the displayed statistics matched MySQL.
- **Generate and Sentence History review:** Both Most frequent and Weighted random generated `Zorvex talvex.` from `zorvex`. History showed the newest result first, the matching algorithm labels, and both repeated-sentence rows highlighted with Times = 2 across algorithms. Max words = 1 produced `Zorvex.` with Weighted random.
- **Longer generation limits:** Imported `length-check.txt`, a single 52-word sentence with unique tokens from `xaa` through `xaz`, then `xba` through `xbz`. The application reported 52 words, 1 sentence, and 52 distinct words. Both algorithms produced `Xaa xab xac.` at Max words = 3 and exactly 50 words ending in `xbx.` at Max words = 50, excluding `xby` and `xbz`.
- The earlier manual application/MySQL checks above cover the running-app generation-length and duplicate-highlighting checks left pending in v1.6. The new automated import tests now cover `Stats.toWordPairs`, the import DAO writes, transaction rollback, and word-length boundaries.
- **Checkpoint complete:** Automated conversion/import verification, isolated test database configuration and documentation, and oversized-word rejection are covered. Stop here before Autocomplete or Batch Generate; DAO methods outside the import path still need their own MySQL verification.

## Version 1.6 — 2026-10-20

**Author:** Ben Quidlat

**Checkpoint:** Words Report and Sentence History tabs implemented on top of the v1.5 DAO methods; Generate tab gets a Max words setting.

### Changes

- **`ui/Background.java` (new):** Small helper that runs a DAO call in a `javafx.concurrent.Task` on a daemon thread and delivers success/failure callbacks on the FX thread. `describe(Throwable)` turns a failure into a readable message that includes the root cause (e.g. "Could not load words (db.properties not found ...)").
- **`WordsReportController.java` / `words_report.fxml`:** Loads `WordDAO.findAll()` when the tab is opened and on Refresh. Words sit in a `FilteredList` → `SortedList` bound to the table's sort order, so the filter and sort survive a refresh. Filter is a case-insensitive substring match. Count, Start count, and End count are editable (`TextFieldTableCell` with a lenient integer converter); edits are validated (no negatives, start/end ≤ total, whole numbers only) and saved with `WordDAO.update` in a background task. A failed save reverts the cell and shows the error. Only one save per word runs at a time: further edits to a word whose save is still running are rejected with a message, and the background task saves a snapshot copy of the `Word`, so an older save can't finish last or undo a newer edit (review fix). The Word column is read-only to avoid UNIQUE-key collisions. Added a status label (word count / "N of M match the filter" / save results).
- **`SentenceHistoryController.java` / `sentence_history.fxml`:** Loads `SentenceDAO.findAll()` (newest first) when the tab is opened and on Refresh. Columns wired with `fx:id`s; added a **Times** column showing how often each sentence was generated. A row factory adds the `duplicate` style class to sentences that occur more than once; status label summarizes the count.
- **`MainController.java` (new) / `main.fxml`:** `main.fxml` now has a controller. It listens for the Words Report and Sentence History tabs being selected and calls their `refresh()`, so both tabs show the latest imports and generated sentences without pressing Refresh. The tabs no longer load at startup (only when opened). `refresh()` does nothing if a load is already running.
- **`generate.fxml` / `GenerateController.java`:** Added an editable **Max words** spinner (1–100, default `SentenceGenerator.DEFAULT_MAX_WORDS` = 20); `onGenerate()` now calls `generate(startWord, maxWords)`. The typed text is validated when Generate runs, because an editable spinner does not commit typed text until Enter or focus loss and Enter in the start-word field skips both. Invalid text shows "Max words must be a whole number from 1 to 100." and does not start a Task. A lenient converter keeps the previous value instead of throwing on text like "abc".
- **`ui/app.css` (new):** `duplicate` row highlight, with selection colors preserved.
- **Tests (new):** `WordsReportControllerTest` (6) for filtering, count validation, and the integer converter; `SentenceHistoryControllerTest` (4) for duplicate counting and the summary text; `GenerateControllerTest` (3) for the Max words parsing rules.
- **`README.md`:** Updated implemented / still-to-do lists. Removed stray Markdown escapes inside code (`sentencebuilder.\*` in both `GRANT` examples, `ui/\*.fxml`, `first\_seen`, `utf8mb4\_bin`) so copied commands work (review fix).
- **`.gitignore`:** Added `samples/` so local test texts are not committed.

### Verification

- Main and test sources compile with `javac --release 21`; all 28 unit tests pass (Maven was not available on the dev machine, so tests were run with a reflection-based runner over the same classes).
- Headless JavaFX smoke test (no database configured): both FXML files and `main.fxml` load; load failures show the `db.properties` message and re-enable Refresh; default sort is alphabetical and column sort works; filter "CAT" shows `cat`, `concatenate`; invalid and non-numeric edits are rejected without saving; a valid edit whose save fails is reverted; duplicate rows receive the `duplicate` class and the Times column shows the right counts.
- Ran the app against a local MySQL 8.0.43 server with all six tabs visible. Imported a 2,058-word original sample text (`the-storm-keeper.txt`, not committed); the Import tab reported "Imported 2058 words in 190 sentences (529 distinct)". Checked in MySQL: 529 rows in `word` with `total_count` summing to 2,058, start and end counts each summing to 190, 1,351 rows in `word_pair`, and one `source_file` row.
- Words Report with that data: Refresh loads the words in alphabetical order, and spot-checked counts match MySQL (e.g. `his` total 48, start 21).
- Words Report filter narrows the table and updates the status line; a count edit shows "Saved" and the new value is still there after restarting the app and refreshing.
- Auto-refresh (read-only headless check against the local MySQL): neither tab loads at startup; selecting Words Report showed "529 words" and selecting Sentence History showed "32 sentences, 5 generated more than once (highlighted)", including sentences generated in the app after the tabs were built.
- Max words (headless check): `generate.fxml` loads, the spinner starts at 20, committing "abc" or "500" keeps 20, committing "35" sets 35.
- **Pending:** in the running app, generate with Max words set low (e.g. 3) and high (e.g. 50) and confirm sentence length; confirm Sentence History duplicate highlighting in the running app (test rows with repeated sentences are in the local DB; the Generate tab is now on this branch after merging main); run `mvn test`.

## Version 1.5 — 2026-10-20

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
- **`changelog.md`:** Recorded these changes (originally numbered 1.4 on the `implement-dao-methods` branch; renumbered to 1.5 when merged with Tien Phan's 1.4) and updated the current version header.

### Verification

- Ran `mvn clean compile` and `mvn test` to verify all modified DAO classes compile cleanly under Java 21 and all existing unit tests continue to pass.
- Pending verification: End-to-end MySQL query and mutation checks for these DAO methods once wired into the Words Report, Sentence History, Autocomplete, and Batch Generate UI controllers.

## Version 1.4 — 2026-10-19

**Author:** Tien Phan

**Checkpoint:** Wire Generate tab to generators and SentenceDAO.

### Changes

- **`GenerateController.java`:** Implemented `onGenerate()`. Validates and lowercases the start word on the UI thread, selects Weighted random or Most frequent, then runs word lookup, generation, and `SentenceDAO.insert` inside a background `Task`. Shows the sentence or error message and re-enables the button in `setOnSucceeded` / `setOnFailed`.
- **`generate.fxml`:** Added `onAction="#onGenerate"` on the start-word field so Enter also generates.
- **`README.md`:** Updated the status note and "What works vs. what's stubbed": import persistence, `MostFrequentGenerator`, and the Generate tab are now listed as working; remaining work is grouped by tab with the DAO methods each needs; suggested order refreshed.
- **`MostFrequentGenerator.java`:** Replaced the outdated "STUB" Javadoc and leftover TODO with a description of the implemented loop-avoidance rule. No behavior change.
- **`changelog.md`:** Added this checkpoint entry.

### Verification

- `mvn clean test`: all 15 unit tests pass (Tokenizer 8, WeightedRandomGenerator 6, MostFrequentGenerator 1).
- Blank start word shows "Please enter a starting word." and does not start a Task.
- Unknown word (not in DB) shows `Could not generate: "…" isn't in the database yet…` and re-enables Generate.
- Known word with Weighted random produces a capitalized sentence ending in `.`, displays it, and inserts a `generated_sentence` row.
- Known word with Most frequent does the same with that algorithm name stored.
- Generate stays disabled while "Generating…" is shown and is re-enabled after success or failure (no stuck button after an error).
-- Imported a local test file containing `Zibble frump wobbles. Zibble frump wobbles. Zibble frump hops. Zibble plonk naps.` (made-up words so existing data can't affect results).
- Most frequent from `zibble` produced `Zibble frump wobbles.` on every click; Weighted random produced all three expected sentences (`… wobbles.`, `… hops.`, `Zibble plonk naps.`).
- `generated_sentence` rows matched each successful click with lowercase `start_word` and the correct `algorithm`; no rows were saved for blank or unknown start words.

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
