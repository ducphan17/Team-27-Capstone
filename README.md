# SentenceBuilder

A statistical "auto-complete on steroids": imports text files, stores word and
word-pair statistics in MySQL, and generates sentences from them.
Java 21 · JavaFX 21 (FXML) · Maven · MySQL 8 (JDBC) · JUnit 5.

> Work in progress: importing and the Generate tab work; other tabs are still being built.
> See [What works vs. what's stubbed](#what-works-vs-whats-stubbed).

## Setup

### 1. Prerequisites
- JDK 21 or newer (`java -version`)
- Maven 3.9+ (`mvn -v`)
- Git
- MySQL 8.4 running locally

### 2. Clone the project
```sh
git clone https://github.com/ducphan17/Team-27-Capstone.git
cd Team-27-Capstone
```

### 3. Install and start MySQL

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

### 4. Create the database

From the project root, run:

```sh
mysql -u root -p < src/main/resources/schema.sql
```

Enter the MySQL root password when prompted. This creates the `sentencebuilder`
database and its four tables.

### 5. Create the application database user

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

### 6. Configure `db.properties`

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

### 7. Run
```sh
mvn clean javafx:run
```
From an IDE, run `edu.sentencebuilder.ui.Launcher` (not `MainApp`) — see design notes.

### 8. Test
```sh
mvn test
```
Tests use in-memory fakes and do not need MySQL.

## Project layout
```
src/main/java/edu/sentencebuilder/
  model/      Word, WordPair, SourceFile, GeneratedSentence
  dao/        Database (connections), WordDAO, WordPairDAO, FileDAO, SentenceDAO
  parser/     Tokenizer, FileImporter (javafx.concurrent.Task)
  generator/  WordStatsSource, Follower, SentenceGenerator, WeightedRandomGenerator, MostFrequentGenerator
  ui/         MainApp, Launcher, one controller per tab
src/main/resources/
  schema.sql
  edu/sentencebuilder/ui/*.fxml   main.fxml + one file per tab (fx:include)
```

## What works vs. what's stubbed

**Working**
- `pom.xml`: compiles, tests, `javafx:run`.
- `Database`: loads `db.properties`, opens connections.
- `WordDAO.findByText / upsertCounts`, `WordPairDAO.getFollowers / getWord / upsertCounts`, `FileDAO.insert`, `SentenceDAO.insert`.
- `Tokenizer` first version (+ 8 tests).
- `FileImporter`: reads the file, counts totals/starts/ends/pairs in HashMaps, reports progress, then saves
  words → pairs → `source_file` row in one transaction (rolled back if any step fails).
- `SentenceGenerator` loop with all three stop rules; `WeightedRandomGenerator` (+ 6 tests);
  `MostFrequentGenerator` skips a (current, next) pair already used in the sentence (+ 1 test).
- Import tab: FileChooser, progress bar and status bound to the task.
- Generate tab: start word (lowercased; Enter or button) + algorithm; generates on a background Task,
  saves to `generated_sentence`, shows a clear message for blank or unknown words.

**Stubbed (TODO comments explain each)**

| Tab | DAO work needed | Tab work needed |
|---|---|---|
| Words Report | `WordDAO.findAll`, `WordDAO.update` | load in a Task; `FilteredList` + `SortedList`; editable cells, no deletes |
| Sentence History | `SentenceDAO.findAll` | give columns `fx:id`s; highlight duplicate sentences |
| Batch Generate | new `WordDAO` query for words with `start_count > 0`; `SentenceDAO.markAddedToCorpus` | start words weighted by `start_count`; "Add to corpus" needs `FileImporter` to accept text, not only a `Path` |
| Autocomplete | `WordDAO.insertIfAbsent`, `WordPairDAO.incrementUserCount` | top followers after space/comma/period; record choices; ignore stale lookups |

Also open:
- `FileDAO.findAll` (no tab uses it yet; only needed for an import-history view).
- Tokenizer edge cases (abbreviations, hyphens/dashes, quotes after periods, numbers, ellipses).
- No automated tests for the UI controllers or DAOs; those are checked manually against MySQL (see `changelog.md`).

Suggested order: Words Report → Sentence History → Batch Generate → Autocomplete → Tokenizer edge cases.

## Design notes
- **`Launcher` class.** If the main class extends `Application` and JavaFX is on the classpath, the JVM aborts with "JavaFX runtime components are missing". A plain `main` that calls `MainApp.main()` skips that check, so the app runs from any IDE without `module-info.java`.
- **`WordStatsSource` + `Follower`.** Generators depend on this interface, not on DAOs, so tests use an in-memory fake. `getFollowers` returns `Follower(text, count, userCount)` rather than `WordPair` so generators work with words, not database IDs.
- **`DataAccessException`.** `WordStatsSource` methods can't throw `SQLException`, so `WordPairDAO` wraps it in this unchecked exception; it surfaces through the Task's `onFailed`.
- **Injected `Random`.** Generators accept a `Random` so tests can seed it and get repeatable results.
- **Counting before writing.** `FileImporter` aggregates the whole file in memory first, so the DB sees one upsert per distinct word/pair instead of one per occurrence.
- **Row-alias upserts.** TODOs use `INSERT ... AS new ON DUPLICATE KEY UPDATE x = x + new.x`; the older `VALUES(x)` form is deprecated in MySQL 8.0.20+.
- **`utf8mb4_bin` on `word.text`.** The default collation is accent- and case-insensitive, so the UNIQUE key would treat "café" and "cafe" as duplicates.
# CS-Project
