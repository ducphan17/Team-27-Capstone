# Changelog

Current version: **1.2**

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
