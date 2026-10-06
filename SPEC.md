# SentenceBuilder — Starter Code Spec

I'm building a Java course project called SentenceBuilder: a statistical "auto-complete on steroids" language model. It reads text files, stores word and word-pair statistics in MySQL, and generates sentences from them. I need STARTER CODE ONLY — a clean, compiling skeleton I will build out myself. Do not implement the full project.

## Tech stack (fixed)
- Java 21 (compile with `<maven.compiler.release>21</maven.compiler.release>`), JavaFX 21 (FXML for views), Maven with javafx-maven-plugin
- MySQL 8 via JDBC (mysql-connector-j), no ORM
- JUnit 5 for tests
- Maven groupId: edu.sentencebuilder, artifactId: sentencebuilder
- Must run with `mvn clean javafx:run` and open cleanly in IntelliJ or VS Code

## What the app does (tabs)
- **Import:** choose a .txt file, parse it, and add word/pair statistics to the DB, with a progress bar. Records the file in source_file.
- **Generate:** user enters a starting word and picks an algorithm; shows one generated sentence and saves it to generated_sentence.
- **Batch Generate:** generate N sentences from random starting words (weighted by start_count), show them, and an "Add to corpus" button feeds them back through the importer.
- **Autocomplete:** user types in a text field; when they type a space, comma, or period, show the top followers of the last completed word. Choosing or typing a known word increments word_pair.user_count; an unknown word is added to the word table.
- **Words Report:** sortable, filterable table of all words (alphabetical by default; also by count, start count, end count). Allows editing word info. No deletes.
- **Sentence History:** all generated sentences, with duplicates highlighted.

## Package structure (base package: edu.sentencebuilder)
- `model/` — Plain data classes: Word, WordPair, SourceFile, GeneratedSentence
- `dao/` — Database class (loads connection settings from db.properties, not hardcoded) and WordDAO, WordPairDAO, FileDAO, SentenceDAO
- `parser/` — Tokenizer, FileImporter
- `generator/` — WordStatsSource interface, abstract SentenceGenerator, WeightedRandomGenerator, MostFrequentGenerator
- `ui/` — MainApp (JavaFX Application), Launcher (main), one controller + one FXML file per tab

## Database (MySQL 8; put in src/main/resources/schema.sql)
Database name: `sentencebuilder`. Use utf8mb4 everywhere.

- **source_file**: file_id INT AUTO_INCREMENT PK, filename VARCHAR(255), word_count INT, sentence_count INT, imported_at DATETIME DEFAULT CURRENT_TIMESTAMP
- **word**: word_id INT AUTO_INCREMENT PK, text VARCHAR(100) COLLATE utf8mb4_bin UNIQUE, total_count INT DEFAULT 0, start_count INT DEFAULT 0, end_count INT DEFAULT 0, first_seen DATETIME DEFAULT CURRENT_TIMESTAMP
- **word_pair**: from_word_id INT, to_word_id INT, count INT DEFAULT 0, user_count INT DEFAULT 0, PRIMARY KEY (from_word_id, to_word_id), FOREIGN KEYs on both columns to word.word_id
- **generated_sentence**: id INT AUTO_INCREMENT PK, sentence TEXT, start_word VARCHAR(100), algorithm VARCHAR(50), created_at DATETIME DEFAULT CURRENT_TIMESTAMP, added_to_corpus BOOLEAN DEFAULT FALSE

**db.properties** keys: `db.url` (e.g. `jdbc:mysql://localhost:3306/sentencebuilder`), `db.user`, `db.password`. Load it from the project root, falling back to the classpath. Commit only `db.properties.example`.

## What to actually implement in this starter
1. **pom.xml** that builds and runs.
2. **Database** connection class + one working DAO method each (e.g., WordDAO.findByText, FileDAO.insert). Use PreparedStatement everywhere. Leave other methods as stubs with TODO comments describing what they should do.
3. **Tokenizer**, working first version:
   - Lowercase all text.
   - Split into sentences on `.` `!` `?`
   - Split sentences into words; strip other punctuation (commas, quotes, semicolons, colons, parentheses) but keep apostrophes inside words (don't, it's).
   - Add a TODO list of edge cases to handle later: abbreviations (Mr., Mrs., Dr.), hyphens and em-dashes, quotes after periods, numbers, ellipses.
   - JUnit 5 tests checking: lowercasing; sentence splitting on all three end marks; apostrophes kept; other punctuation stripped; empty/whitespace input returns no sentences.
4. **FileImporter** as a `javafx.concurrent.Task<Void>` that reads a file, counts words/pairs/starts/ends in HashMaps, calls `updateProgress()`, then writes to the DB with batched `INSERT ... ON DUPLICATE KEY UPDATE`. Implement the counting; stub the DB write with clear TODOs. In that TODO, note the ordering problem: words must be inserted first so their IDs exist before word_pair rows are inserted.
5. **Generators**:
   - Generators must not talk to the database directly. Define a `WordStatsSource` interface (e.g., `getFollowers(String word)`, `getWord(String text)`); DAOs implement it; inject it into generators via the constructor so they can be unit-tested with fake in-memory data.
   - `SentenceGenerator` is abstract, using the Template Method pattern: a concrete `generate(String startWord, int maxWords)` loop and an abstract `chooseNext(...)` method.
   - Stop when: a random draw falls under end_count/total_count for the current word, the current word has no followers, or maxWords is reached (default 20).
   - Implement `WeightedRandomGenerator` fully: pick the next word with probability proportional to (count + user_count).
   - Leave `MostFrequentGenerator` as a stub with a note about avoiding loops like "of the of the".
6. **UI**: a TabPane using `fx:include` so each tab has its own FXML and controller. Only the Import tab needs to work (FileChooser + ProgressBar bound to the task + status label). Other tabs: placeholder layout with labeled controls and TODOs.
   - Add a separate `Launcher` class with `main()` that calls `MainApp.main()`, to avoid "JavaFX runtime components are missing" when running from an IDE. Do not use module-info.java.

## Code quality
- Short Javadoc on every class explaining its responsibility.
- Never run DB work on the JavaFX Application Thread.
- No hardcoded passwords.

## Tests
- Tokenizer tests (listed above), plus a WeightedRandomGenerator test using a fake WordStatsSource. No test may require a running database.

## Process
1. Check the environment with `java -version` and `mvn -v` and report any problems.
2. Generate the code.
3. Run `mvn clean compile` and `mvn test` and fix errors until both pass. Do not run `mvn javafx:run`; I'll do that myself.
4. Git: this folder sits inside a parent git repo (Fall-2026/). Make CS-Project its own separate repo: run `git init` here, add `CS-Project/` to the parent repo's .gitignore, create a .gitignore here (target/, .idea/, .vscode/, db.properties, .DS_Store), and make one initial commit.
5. Create a CLAUDE.md summarizing the stack, package structure, and rules (no DB work on the FX thread, PreparedStatement only, generators use the WordStatsSource interface).

## After generating
Give me: (1) a README.md with setup steps (create DB, run schema.sql, configure db.properties, run the app), and (2) a short list of what's stubbed vs. working so I know what to build next. Explain any non-obvious design decision in 1–2 sentences.