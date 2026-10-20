# SentenceBuilder

A statistical "auto-complete on steroids": imports text files, stores word and
word-pair statistics in MySQL, and generates sentences from them.
Java 21 · JavaFX 21 (FXML) · Maven · MySQL 8 (JDBC) · JUnit 5.

> Work in progress: importing, the Generate tab, Words Report, and Sentence History work;
> Batch Generate and Autocomplete are still being built.
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

Tests use in-memory fakes and do not need MySQL.

## Project layout

```
src/main/java/edu/sentencebuilder/
  model/      Word, WordPair, SourceFile, GeneratedSentence
  dao/        Database (connections), WordDAO, WordPairDAO, FileDAO, SentenceDAO, DataAccessException
  parser/     Tokenizer, FileImporter (javafx.concurrent.Task)
  generator/  WordStatsSource, Follower, SentenceGenerator, WeightedRandomGenerator, MostFrequentGenerator
  ui/         MainApp, Launcher, one controller per tab
src/main/resources/
  schema.sql
  edu/sentencebuilder/ui/*.fxml   main.fxml + one file per tab (fx:include)
```

## What works vs. what's left

**Implemented**

* `pom.xml`: compiles, tests, `javafx:run`.
* `Database`: loads `db.properties`, opens connections.
* **Import:** `FileImporter` reads the file, counts totals/starts/ends/pairs in HashMaps, reports progress, then saves words, word pairs, and the `source_file` record in one transaction (commit on success, rollback on failure). Repeated imports add to existing counts. Import tab: FileChooser, progress bar and status bound to the task.
* **Word DAO:** `findByText`, `findAll`, `insertIfAbsent`, `upsertCounts`, `update`.
* **Word-pair DAO:** `getFollowers`, `getWord`, `upsertCounts`, `incrementUserCount`.
* **File DAO:** `insert`, `findAll`.
* **Sentence DAO:** `insert`, `findAll`, `markAddedToCorpus`.
* **Generators:** `SentenceGenerator` loop with all three stop rules; `WeightedRandomGenerator`; `MostFrequentGenerator` skips a (current, next) pair already used in the sentence.
* `Tokenizer` first version.
* **Generate tab:** start word (lowercased; Enter or button) + algorithm + Max words (1–100, default 20); generates on a background Task, saves to `generated_sentence`, shows a clear message for blank or unknown words.
* **Words Report tab:** loads all words in a background `Task`; sortable columns (alphabetical by default), case-insensitive filter, and in-place editing of the three counts with validation (no negatives; start/end count cannot exceed total). A failed save reverts the cell. Word text is read-only.
* **Sentence History tab:** loads all generated sentences (newest first) in a background `Task`, shows how many times each sentence was generated, and highlights repeats (`duplicate` style class in `ui/app.css`).
* **Auto-refresh:** `MainController` (controller for `main.fxml`) reloads Words Report and Sentence History each time their tab is opened, so imports and generated sentences show up without pressing Refresh.
* `ui/Background`: shared helper that runs DAO calls on a daemon `Task` and turns failures into readable status messages.
* 28 unit tests covering the tokenizer (8), `WeightedRandomGenerator` (6), `MostFrequentGenerator` (1), Words Report filtering/validation (6), and Sentence History duplicate counting (4), and Max words parsing (3). All use in-memory data, no database.

**Verification status**

* Importing, repeated imports, and import rollback were checked manually against MySQL (see `changelog.md`, v1.1 and v1.2).
* The Generate tab, Words Report (`WordDAO.findAll / update`), and Sentence History (`SentenceDAO.findAll`) were checked manually against MySQL (see `changelog.md`, v1.4 and v1.6).
* `WordDAO.insertIfAbsent`, `WordPairDAO.incrementUserCount`, `FileDAO.findAll`, and `SentenceDAO.markAddedToCorpus` are implemented but not yet checked against MySQL.
* No automated tests for the DAOs; setting up an isolated test database is still pending.

**Still to do**

| Tab | DAO work needed | Tab work needed |
|---|---|---|
| Batch Generate | new `WordDAO` query for words with `start_count > 0` | start words weighted by `start_count`; "Add to corpus" needs `FileImporter` to accept text, not only a `Path`; then `SentenceDAO.markAddedToCorpus` |
| Autocomplete | none (uses `insertIfAbsent`, `incrementUserCount`) | top followers after space/comma/period; record choices; ignore stale lookups |

Also open:
* `FileDAO.findAll` (no tab uses it yet; only needed for an import-history view).
* Tokenizer edge cases (abbreviations, hyphens/dashes, quotes after periods, numbers, ellipses).

Suggested order: Batch Generate → Autocomplete → Tokenizer edge cases.

## Design notes

* **`Launcher` class.** If the main class extends `Application` and JavaFX is on the classpath, the JVM aborts with "JavaFX runtime components are missing". A plain `main` that calls `MainApp.main()` skips that check, so the app runs from any IDE without `module-info.java`.
* **`WordStatsSource` + `Follower`.** Generators depend on this interface, not on DAOs, so tests use an in-memory fake. `getFollowers` returns `Follower(text, count, userCount)` rather than `WordPair` so generators work with words, not database IDs.
* **`DataAccessException`.** `WordStatsSource` methods can't throw `SQLException`, so DAO code wraps it in this unchecked exception; it surfaces through the Task's `onFailed`. The `findAll`, `insertIfAbsent`, `update`, `incrementUserCount`, and `markAddedToCorpus` methods follow this convention. `findByText`, `FileDAO.insert`, `SentenceDAO.insert`, and the importer's `upsertCounts` methods still declare `throws SQLException`.
* **Transactional import.** `WordDAO.upsertCounts`, `WordPairDAO.upsertCounts`, and `FileDAO.insert` take the importer's `Connection` instead of opening their own, so one import commits or rolls back as a unit. Words are saved before pairs because pairs need word IDs.
* **Injected `Random`.** Generators accept a `Random` so tests can seed it and get repeatable results.
* **Counting before writing.** `FileImporter` aggregates the whole file in memory first, so the DB sees one upsert per distinct word/pair instead of one per occurrence.
* **Row-alias upserts.** Upserts use `INSERT ... AS incoming ON DUPLICATE KEY UPDATE x = x + incoming.x`; the older `VALUES(x)` form is deprecated in MySQL 8.0.20+.
* **`insertIfAbsent` uses a no-op update.** It relies on `ON DUPLICATE KEY UPDATE text = word.text` rather than `INSERT IGNORE`, so existing rows and `first_seen` stay untouched and unrelated errors are not silently swallowed.
* **`utf8mb4_bin` on `word.text`.** The default collation is accent- and case-insensitive, so the UNIQUE key would treat "café" and "cafe" as duplicates. Because the collation is binary, `ORDER BY text` sorts uppercase before lowercase, which does not matter since the tokenizer lowercases everything.
* **Words Report edits.** `WordDAO.update` can change `text`, which has a UNIQUE key, so renaming a word to an existing one would throw `DataAccessException`. The tab keeps the Word column read-only and edits counts only.

