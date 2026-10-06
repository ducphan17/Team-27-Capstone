# SentenceBuilder — notes for Claude

Course project: JavaFX app that imports text, stores word / word-pair statistics in MySQL,
and generates sentences. Started as a starter skeleton; the student builds it out.
`SPEC.md` is the original spec; `README.md` lists what is working vs. stubbed.

## Stack
- Java 21 (`maven.compiler.release` 21), JavaFX 21 with FXML, Maven + javafx-maven-plugin
- MySQL 8 via plain JDBC (mysql-connector-j), no ORM; schema in `src/main/resources/schema.sql`
- JUnit 5
- No `module-info.java` (classpath app). Entry point for IDEs and `javafx:run` is `ui.Launcher`.

## Packages (`edu.sentencebuilder`)
- `model/` plain data classes: Word, WordPair, SourceFile, GeneratedSentence
- `dao/` Database (reads `db.properties` from project root, then classpath), WordDAO, WordPairDAO, FileDAO, SentenceDAO, DataAccessException
- `parser/` Tokenizer, FileImporter (`Task<Void>`)
- `generator/` WordStatsSource, Follower, SentenceGenerator (template method), WeightedRandomGenerator, MostFrequentGenerator
- `ui/` MainApp, Launcher, one controller per tab; FXML in `src/main/resources/edu/sentencebuilder/ui/` (`main.fxml` uses `fx:include`)

## Rules
- Never do DB work on the JavaFX Application Thread. Use `javafx.concurrent.Task` on a daemon thread; touch controls only in `setOnSucceeded`/`setOnFailed` or via bindings.
- PreparedStatement only — no string-concatenated SQL. Use try-with-resources for Connection/Statement/ResultSet.
- Generators never touch the database: they depend on the `WordStatsSource` interface, injected via constructor. `WordPairDAO` is the real implementation; tests use in-memory fakes.
- Tests must not require a running database.
- No hardcoded credentials. `db.properties` is git-ignored; only `db.properties.example` is committed.
- Words are never deleted (Words Report allows edits only).
- Insert/upsert words before word pairs (pairs need word IDs). Use `INSERT ... AS new ON DUPLICATE KEY UPDATE` (not deprecated `VALUES()`).
- Every class gets a short Javadoc describing its responsibility.

## Commands
- `mvn clean compile`, `mvn test`
- `mvn clean javafx:run` opens a window — leave running it to the user.
