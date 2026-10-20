# SentenceBuilder

A statistical "auto-complete on steroids": imports text files, stores word and
word-pair statistics in MySQL, and generates sentences from them.
Java 21 · JavaFX 21 (FXML) · Maven · MySQL 8 (JDBC) · JUnit 5.

> Work in progress. The project started from a starter skeleton; the persistence
> layer and both generators are now implemented, and the remaining work is mostly
> the UI tabs. See \[What works vs. what's left](#what-works-vs-whats-left).

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

GRANT ALL PRIVILEGES ON sentencebuilder.\*
TO 'sentencebuilder'@'localhost';

FLUSH PRIVILEGES;
exit;
```

If the user already exists, use `ALTER USER` instead of `CREATE USER`:

```sql
ALTER USER 'sentencebuilder'@'localhost'
IDENTIFIED BY 'choose-a-password';

GRANT ALL PRIVILEGES ON sentencebuilder.\*
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
  edu/sentencebuilder/ui/\*.fxml   main.fxml + one file per tab (fx:include)
```

## What works vs. what's left

**Implemented**

* `pom.xml`: compiles, tests, `javafx:run`.
* `Database`: loads `db.properties`, opens connections.
* **Import:** `FileImporter` reads the file, counts totals/starts/ends/pairs in HashMaps, reports progress, then saves words, word pairs, and the `source\_file` record in one transaction (commit on success, rollback on failure). Repeated imports add to existing counts. Import tab: FileChooser, progress bar and status bound to the task.
* **Word DAO:** `findByText`, `findAll`, `insertIfAbsent`, `upsertCounts`, `update`.
* **Word-pair DAO:** `getFollowers`, `getWord`, `upsertCounts`, `incrementUserCount`.
* **File DAO:** `insert`, `findAll`.
* **Sentence DAO:** `insert`, `findAll`, `markAddedToCorpus`.
* **Generators:** `SentenceGenerator` loop with all three stop rules; `WeightedRandomGenerator`; `MostFrequentGenerator` with consecutive-bigram loop avoidance.
* `Tokenizer` first version.
* 15 unit tests covering the tokenizer (8), `WeightedRandomGenerator` (6), and `MostFrequentGenerator` (1). All use in-memory fakes, no database.

**Verification status**

* Importing, repeated imports, and import rollback were checked manually against MySQL (see `changelog.md`, v1.1 and v1.2).
* `WordDAO.findAll / insertIfAbsent / update`, `WordPairDAO.incrementUserCount`, `FileDAO.findAll`, and `SentenceDAO.findAll / markAddedToCorpus` are implemented but have not yet been checked against MySQL.
* There are no automated integration tests yet; setting up an isolated test database is still pending.

**Still to do**

* Generate, Batch Generate, Autocomplete, Words Report, and Sentence History tabs: layout only. The DAO methods they need now exist; the controllers still need to call them from background `Task`s.
* Wire the generators to the real database (`WordPairDAO` as the `WordStatsSource`) in the UI.
* Tokenizer edge cases (abbreviations, hyphens/dashes, quotes after periods, numbers, ellipses).

Suggested order: Generate tab → Words Report → Sentence History → Autocomplete → Batch Generate.

## Design notes

* **`Launcher` class.** If the main class extends `Application` and JavaFX is on the classpath, the JVM aborts with "JavaFX runtime components are missing". A plain `main` that calls `MainApp.main()` skips that check, so the app runs from any IDE without `module-info.java`.
* **`WordStatsSource` + `Follower`.** Generators depend on this interface, not on DAOs, so tests use an in-memory fake. `getFollowers` returns `Follower(text, count, userCount)` rather than `WordPair` so generators work with words, not database IDs.
* **`DataAccessException`.** `WordStatsSource` methods can't throw `SQLException`, so DAO code wraps it in this unchecked exception; it surfaces through the Task's `onFailed`. The `findAll`, `insertIfAbsent`, `update`, `incrementUserCount`, and `markAddedToCorpus` methods follow this convention. `findByText`, `FileDAO.insert`, `SentenceDAO.insert`, and the importer's `upsertCounts` methods still declare `throws SQLException`.
* **Transactional import.** `WordDAO.upsertCounts`, `WordPairDAO.upsertCounts`, and `FileDAO.insert` take the importer's `Connection` instead of opening their own, so one import commits or rolls back as a unit. Words are saved before pairs because pairs need word IDs.
* **Injected `Random`.** Generators accept a `Random` so tests can seed it and get repeatable results.
* **Counting before writing.** `FileImporter` aggregates the whole file in memory first, so the DB sees one upsert per distinct word/pair instead of one per occurrence.
* **Row-alias upserts.** Upserts use `INSERT ... AS incoming ON DUPLICATE KEY UPDATE x = x + incoming.x`; the older `VALUES(x)` form is deprecated in MySQL 8.0.20+.
* **`insertIfAbsent` uses a no-op update.** It relies on `ON DUPLICATE KEY UPDATE text = word.text` rather than `INSERT IGNORE`, so existing rows and `first\_seen` stay untouched and unrelated errors are not silently swallowed.
* **`utf8mb4\_bin` on `word.text`.** The default collation is accent- and case-insensitive, so the UNIQUE key would treat "café" and "cafe" as duplicates. Because the collation is binary, `ORDER BY text` sorts uppercase before lowercase, which does not matter since the tokenizer lowercases everything.
* **Words Report edits.** `WordDAO.update` can change `text`, which has a UNIQUE key. Renaming a word to an existing one throws `DataAccessException`, so the tab should show that message.

