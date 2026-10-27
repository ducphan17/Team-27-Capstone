# SentenceBuilder

A statistical "auto-complete on steroids": imports text files, stores word and
word-pair statistics in MySQL, and generates sentences from them.
Java 21 · JavaFX 21 (FXML) · Maven · MySQL 8 (JDBC) · JUnit 5.

> All six tabs work: Import, Generate, Batch Generate, Autocomplete, Words Report,
> and Sentence History. Tokenizer edge cases are next.
> See [What works vs. what's left](#what-works-vs-whats-left).

## Setup

### 1\. Prerequisites

* JDK 21 or newer (`java -version`)
* Maven 3.9+ (`mvn -v`)
* Git
* MySQL 8.4 running locally

### 2\. Clone the project

```sh
git clone https://github.com/ducphan17/Team-27-Capstone.git
cd Team-27-Capstone
```

### 3\. Install and start MySQL

#### macOS

Install MySQL 8.4 with Homebrew:

```sh
brew install mysql@8.4
brew services start mysql@8.4
```

#### Windows

Install MySQL 8.4 using the [MySQL Installer](https://dev.mysql.com/downloads/installer/).
Remember the root password and keep the default port `3306`. Make sure the
`MySQL80` service is running from Windows Services.

### 4\. Create the database

From the project root, run:

```sh
mysql -u root -p < src/main/resources/schema.sql
```

Enter the MySQL root password when prompted. This creates the `sentencebuilder`
database and its four tables.

### 5\. Create the application database user

Open MySQL:

```sh
mysql -u root -p
```

At the `mysql>` prompt, run the following. Replace `choose-a-password` with a
password of your choice:

```sql
CREATE USER 'sentencebuilder'@'localhost'
IDENTIFIED BY 'choose-a-password';

GRANT ALL PRIVILEGES ON sentencebuilder.*
TO 'sentencebuilder'@'localhost';

FLUSH PRIVILEGES;
exit;
```

If the user already exists, use `ALTER USER` instead of `CREATE USER`:

```sql
ALTER USER 'sentencebuilder'@'localhost'
IDENTIFIED BY 'choose-a-password';

GRANT ALL PRIVILEGES ON sentencebuilder.*
TO 'sentencebuilder'@'localhost';

FLUSH PRIVILEGES;
exit;
```

### 6\. Configure `db.properties`

#### macOS

```sh
cp db.properties.example db.properties
```

#### Windows PowerShell

```powershell
Copy-Item db.properties.example db.properties
```

Edit `db.properties`:

```properties
db.url=jdbc:mysql://localhost:3306/sentencebuilder
db.user=sentencebuilder
db.password=choose-a-password
```

Use the same password you used when creating the MySQL user. The app reads
`db.properties` from the project root (the working directory), falling back to
the classpath. `db.properties` is git-ignored — never commit it.

### 7\. Run

```sh
mvn clean javafx:run
```

From an IDE, run `edu.sentencebuilder.ui.Launcher` (not `MainApp`) — see design notes.

### 8\. Test

```sh
mvn test
```

Unit tests use in-memory data and do not need MySQL. Import integration tests are
opt-in; follow [Import checkpoint: database tests](#import-checkpoint-database-tests).

## Import checkpoint: database tests

### 1. Review and run the focused unit tests

`FileImporterStatsTest` verifies pair IDs/counts, empty pairs, and missing IDs.
`FileImporterValidationTest` verifies the 100/101-character boundary, Unicode
character counting, and rejection before a connection opens:

```sh
mvn -Dtest=FileImporterStatsTest,FileImporterValidationTest test
```

`FileImporter.call` uses shared `TextImporter` validation for every distinct, normalized word immediately after
counting, before announcing "Saving to database...". `writeToDatabase` repeats
validation before opening a connection, protecting direct callers too.
The limit is 100 Unicode code points, matching `word.text VARCHAR(100)`.
`Word.MAX_WORD_LENGTH` and `Word.validateTextLength` define the shared rule.
`WordDAO.insertIfAbsent`, `upsertCounts`, and `update` also validate before writing,
so callers such as Autocomplete cannot bypass the limit. The importer wraps a
length failure in `InvalidImportException`, with a shortened preview and "No data
was imported." The existing Import tab failure handler displays that error;
oversized words are never truncated.

The original importer constructor still uses application database connections.
A package-private constructor accepts a connection factory for tests, and the
transaction method has package access so tests can run without starting JavaFX.
Comments in the implementation explain validation, connection ownership, and rollback.

### 2. Create the isolated database and user

Start MySQL 8.4 as described above, then open an administrator session:

```sh
mysql -u root -p
```

Run this SQL, choosing a separate test password:

```sql
CREATE DATABASE sentencebuilder_test
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'sentencebuilder_test'@'localhost'
    IDENTIFIED BY 'choose-a-test-password';
GRANT ALL PRIVILEGES ON sentencebuilder_test.*
    TO 'sentencebuilder_test'@'localhost';
exit;
```

If the database/user already exists, reuse it or change the password with
`ALTER USER`; keep the user's grants limited to `sentencebuilder_test.*`.
The test database is disposable: all four test tables are dropped and recreated
at suite startup, then cleared before and after each test. Do not store app data there or run two test suites against it at once.

Do not use the application's `schema.sql` to initialize test tables: it contains
`USE sentencebuilder`. Integration tests create tables automatically using the
table-only `src/test/resources/schema-test.sql`. Keep its definitions aligned with
the main schema. Rebuilding at suite startup applies changed definitions instead
of retaining old tables through `CREATE TABLE IF NOT EXISTS`. Manual initialization
alone does not migrate existing tables. For manual initialization, run:

```sh
mysql -u sentencebuilder_test -p sentencebuilder_test < src/test/resources/schema-test.sql
```

### 3. Configure separate test credentials

On macOS:

```sh
cp db-test.properties.example db-test.properties
```

On Windows PowerShell:

```powershell
Copy-Item db-test.properties.example db-test.properties
```

Edit the ignored `db-test.properties`:

```properties
db.url=jdbc:mysql://localhost:3306/sentencebuilder_test
db.user=sentencebuilder_test
db.password=choose-a-test-password
```

Keep `db.properties` pointing at your application database. Tests never read it.
The suite requires the dedicated username and checks each connection's selected
database before schema creation, writes, or cleanup. Missing settings, a wrong
database, or an unavailable MySQL server fail the build rather than skip tests.

### 4. Run the complete checkpoint

From the project root:

```sh
mvn clean test
mvn -Pdb-tests verify
git diff --check
```

Plain `mvn test` runs unit tests only. The `db-tests` Maven profile adds Failsafe,
which runs `FileImporterIT` during `verify`. Results are in
`target/surefire-reports` and `target/failsafe-reports`.

The 13 MySQL integration tests check:

* Importing `Zorvex miplex. Zorvex talvex.` twice preserves word IDs and
  `first_seen`, doubles imported counts, creates two file records, and preserves
  an existing `user_count = 7`.
* SQL and runtime failures injected by a test-only `FileDAO` after inserting file
  metadata roll back all import writes. Fresh connections confirm all four tables
  match their previous snapshots, including IDs and timestamps.
* A 100-character word saves successfully.
* Valid words followed by a 101-character word produce the expected error and
  leave all stored rows unchanged.
* `findStartingWords` includes positive start counts and excludes zero counts.
* Generated sentences retain their IDs, text, start word, algorithm, timestamp,
  and initial corpus flag.
* Direct text import saves total/start/end counts, pairs, and source metadata.
* The same `CorpusService.addToCorpus` used by the UI commits imported counts and
  selected sentence flags together; unselected rows remain unmarked. A fresh
  connection sees no changes before the service commits.
* An SQL failure after text import but before marking rolls back all changes.
* A runtime failure after marking rolls back both corpus counts and flags.
* `AutocompleteService.recordSentences` adds unknown words with zero counts, adds 1
  to `user_count` for new and existing pairs, and leaves imported words and pair
  counts unchanged. No pair crosses from one sentence into the next.
* A failure after the new word is inserted, but before the pair is updated, rolls
  back the new word.

The corpus and Autocomplete transaction checks call the production service with guarded test connections
and injected DAO failures; they do not copy its transaction implementation.

Rollback checks compare stored rows, not auto-increment counters; failed
transactions may leave ID gaps.

### 5. Confirm the Import tab message

Create `samples/oversized-check.txt` containing ordinary text followed by a single
101-letter word. For example, using Python 3:

```sh
python3 - <<'PYTHON'
from pathlib import Path
Path("samples").mkdir(exist_ok=True)
Path("samples/oversized-check.txt").write_text("Ordinary words first. " + "a" * 101 + ".", encoding="utf-8")
PYTHON
mvn javafx:run
```

Import the file and expect:

```text
Import failed: Word exceeds the 100-character limit (101 characters): "aaaa…". No data was imported.
```

The actual preview contains the first 30 characters. The Choose File button must
be enabled again. The automated tests verify unchanged database contents through
the same validation/transaction method the UI calls; this last check verifies the
visible JavaFX message.

## Project layout

```
src/main/java/edu/sentencebuilder/
  model/      Word, WordPair, SourceFile, GeneratedSentence, ImportedWordCounts
  dao/        Database (connections), WordDAO, WordPairDAO, FileDAO, SentenceDAO, DataAccessException
  parser/     Tokenizer, FileImporter (javafx.concurrent.Task), TextImporter, CorpusService, AutocompleteService, InvalidImportException
  generator/  WordStatsSource, Follower, SentenceGenerator, WeightedRandomGenerator, MostFrequentGenerator, StartingWordPicker
  ui/         MainApp, Launcher, one controller per tab, AutocompleteInput (text rules), AutocompleteSaves (save-once and retry rules), and PendingSentences (unsaved-sentence file) for Autocomplete
src/main/resources/
  schema.sql
  edu/sentencebuilder/ui/*.fxml   main.fxml + one file per tab (fx:include)
```

## What works vs. what's left

**Implemented**

* `pom.xml`: compiles, tests, `javafx:run`.
* `Database`: loads `db.properties`, opens connections.
* **Import:** rejects normalized words longer than 100 characters before opening a database connection. `FileImporter` reads the file and reports progress. Shared `TextImporter` logic groups total/start/end counters in one map and snapshots them as `ImportedWordCounts` records; pair counts use a separate lookup map. Words, word pairs, and the `source_file` record save in one transaction (commit on success, rollback on failure). Repeated imports add to existing counts. Import tab: FileChooser, progress bar and status bound to the task.
* **Word DAO:** `findByText`, `findAll`, `findStartingWords`, `insertIfAbsent` (also with a caller-owned connection), `upsertCounts(List<ImportedWordCounts>)` with a caller-owned connection, `update`.
* **Word-pair DAO:** `getFollowers`, `getWord`, `upsertCounts`, `incrementUserCount` (also with a caller-owned connection).
* **File DAO:** `insert`, `findAll`.
* **Sentence DAO:** `insert`, `findAll`, `markAddedToCorpus`.
* **Generators:** `SentenceGenerator` loop with all three stop rules; `WeightedRandomGenerator`; `MostFrequentGenerator` skips a (current, next) pair already used in the sentence.
* `Tokenizer` first version.
* **Generate tab:** start word (lowercased; Enter or button) + algorithm + Max words (1–100, default 20); generates on a background Task, saves to `generated_sentence`, shows a clear message for blank or unknown words.
* **Batch Generate tab:** validates a count from 1–1000, chooses starting words weighted by `start_count`, and generates with either algorithm using the default 20-word limit. Background work saves results using one autocommit connection for inserts, retaining partial successes. Empty corpora show an import-first error. Multiple selection feeds eligible results through `CorpusService` and `TextImporter`; counts, pairs, source metadata, and sentence flags commit together. Added rows show `[Added to corpus]` and are excluded from subsequent additions in the current list.
* **Autocomplete tab:**
  * *Suggestions:* shows the top 5 followers (by `count + user_count`) of the last word typed, with their imported and typed counts, as soon as that word has followers; no space is needed, and deleting the space after a word changes nothing. Lookups run after a 150 ms typing pause, the list is cleared at once when the word changes, older lookups are ignored, and a failed lookup is retried on the next edit. Clicking a suggestion (or Enter on it) adds it after the last word.
  * *Saving:* a sentence is saved when it ends with `.`, `!`, or `?`; Enter adds a period and keeps the text, so typing on starts a new sentence. Saving uses the text at that moment, so corrected typos are never stored and picked words count once. `AutocompleteService.recordSentences` saves in one transaction: unknown words get zero counts and `word_pair.user_count` goes up by 1 per neighboring pair. Pairs continue across commas, never across sentence ends; imported counts never change.
  * *Saved once:* sentences are compared by their words, so deleting, changing, or retyping an end mark, or editing another sentence, never re-saves an unchanged sentence. A sentence that continues a saved one (Enter, then the period deleted and more typed) saves only the new pairs, and a shortened one saves nothing; any other edit, or the same sentence typed again, counts as new.
  * *Failed saves:* retried in their own transaction (never blocking new sentences) every 10 seconds for up to 3 attempts, and whenever Enter is pressed. The save thread keeps every unconfirmed sentence in `autocomplete-pending.txt` (git-ignored) before and after each attempt, the app waits up to 5 seconds on close for a running save, and pending sentences are saved on the next start. A commit followed by a close failure counts as saved, so retries never count twice. Words over 100 characters are rejected with a message.
  * *Known behavior:* clearing the field and retyping a sentence counts it again. Autocomplete uses the importer's `Tokenizer`, so `Mr.` or `3.5` ends a sentence (see Tokenizer edge cases below).
* **Words Report tab:** loads all words in a background `Task`; sortable columns (alphabetical by default), case-insensitive filter, and in-place editing of the three counts with validation (no negatives; start/end count cannot exceed total). A failed save reverts the cell. Word text is read-only.
* **Sentence History tab:** loads all generated sentences (newest first) in a background `Task`, shows how many times each sentence was generated, and highlights repeats (`duplicate` style class in `ui/app.css`).
* **Auto-refresh:** `MainController` (controller for `main.fxml`) reloads Words Report and Sentence History each time their tab is opened, so imports and generated sentences show up without pressing Refresh.
* `ui/Background`: shared helper that runs DAO calls on a daemon `Task` and turns failures into readable status messages.
* 90 unit tests covering shared word and DAO validation (6), Autocomplete save validation and transaction outcomes (4), Autocomplete text rules (10), Autocomplete save-once and retry rules (20), the Autocomplete pending-sentence file (4), import count/pair conversion and validation (12), the tokenizer (8), `WeightedRandomGenerator` (6), `MostFrequentGenerator` (1), weighted starting-word selection (4), Words Report filtering/validation (6), Sentence History duplicate counting (4), Max words parsing (3), and batch count parsing (2). All use in-memory data, no database.

**Verification status**

* Importing, repeated imports, and import rollback were checked manually against MySQL (see `changelog.md`, v1.1 and v1.2).
* The Generate tab, Words Report (`WordDAO.findAll / update`), and Sentence History (`SentenceDAO.findAll`) were checked manually against MySQL (see `changelog.md`, v1.4 and v1.6).
* The Autocomplete tab (suggestions while typing, sentence saving, Enter, continued sentences, retries, and the pending file across a restart) was checked manually against MySQL (see `changelog.md`, v1.9).
* `FileDAO.findAll` is implemented but not yet checked against MySQL.
* The 13 automated MySQL integration tests cover import persistence, `WordDAO.findStartingWords`, sentence inserts, `SentenceDAO.markAddedToCorpus` through the production corpus service, and `WordDAO.insertIfAbsent` / `WordPairDAO.incrementUserCount` through the production Autocomplete service, including commit and rollback. They use the isolated test database described above. The contributor also checked batch generation and corpus additions in the running app; see `changelog.md`, v1.8.

**Still to do**

* `FileDAO.findAll` (no tab uses it yet; only needed for an import-history view).
* Tokenizer edge cases (abbreviations, hyphens/dashes, quotes after periods, numbers, ellipses).

Next: Tokenizer edge cases. Every tab is implemented.

## Design notes

* **`Launcher` class.** If the main class extends `Application` and JavaFX is on the classpath, the JVM aborts with "JavaFX runtime components are missing". A plain `main` that calls `MainApp.main()` skips that check, so the app runs from any IDE without `module-info.java`.
* **`WordStatsSource` + `Follower`.** Generators depend on this interface, not on DAOs, so tests use an in-memory fake. `getFollowers` returns `Follower(text, count, userCount)` rather than `WordPair` so generators work with words, not database IDs.
* **`DataAccessException`.** `WordStatsSource` methods can't throw `SQLException`, so DAO code wraps it in this unchecked exception; it surfaces through the Task's `onFailed`. The `findAll`, `insertIfAbsent`, `update`, `incrementUserCount`, and `markAddedToCorpus` methods follow this convention. `findByText`, `FileDAO.insert`, `SentenceDAO.insert`, and the importer's `upsertCounts` methods still declare `throws SQLException`.
* **Transactional import.** `WordDAO.upsertCounts`, `WordPairDAO.upsertCounts`, and `FileDAO.insert` take the importer's `Connection` instead of opening their own, so one import commits or rolls back as a unit. Words are saved before pairs because pairs need word IDs.
* **Injected `Random`.** Generators accept a `Random` so tests can seed it and get repeatable results.
* **Counting before writing.** Shared `TextImporter.Stats` aggregates input in memory with one mutable counter object per word, then produces immutable `ImportedWordCounts` records. The database sees one upsert per distinct word/pair instead of one per occurrence.
* **Corpus feedback.** `CorpusService` owns the Add selected transaction and takes an injectable connection factory. The controller and integration tests call this same service. `TextImporter` handles text without constructing a JavaFX task or a placeholder file path.
* **Row-alias upserts.** Upserts use `INSERT ... AS incoming ON DUPLICATE KEY UPDATE x = x + incoming.x`; the older `VALUES(x)` form is deprecated in MySQL 8.0.20+.
* **`insertIfAbsent` uses a no-op update.** It relies on `ON DUPLICATE KEY UPDATE text = word.text` rather than `INSERT IGNORE`, so existing rows and `first_seen` stay untouched and unrelated errors are not silently swallowed.
* **`utf8mb4_bin` on `word.text`.** The default collation is accent- and case-insensitive, so the UNIQUE key would treat "café" and "cafe" as duplicates. Because the collation is binary, `ORDER BY text` sorts uppercase before lowercase, which does not matter since the tokenizer lowercases everything.
* **Words Report edits.** `WordDAO.update` can change `text`, which has a UNIQUE key, so renaming a word to an existing one would throw `DataAccessException`. The tab keeps the Word column read-only and edits counts only.

