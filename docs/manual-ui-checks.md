# Manual interface checks

Checks the unit tests cannot cover because they need the running JavaFX app.
Run the app against your local database (not the `sentencebuilder_test` database),
tick each box, and record results and the date in `changelog.md`.

"Stop MySQL" means stopping the server while the app stays open
(`net stop MySQL80` in an administrator PowerShell, or Ctrl+C in the window running `mysqld --console`).

## Generate

- [ ] The Algorithm box lists **Weighted random** (selected) and **Most frequent**.
- [ ] Generate once with each algorithm. Sentence History shows the matching name in the Algorithm column.

## Import history

- [ ] At startup the history table lists earlier imports, newest first, and the label under it shows the import count and total words.
- [ ] Import a `.txt` file. A new row appears at the top as soon as the import finishes.
- [ ] Batch Generate → Add selected, then open Import again. A `batch-generated` row appears at the top.
- [ ] Stop MySQL, open another tab, then Import. The label shows a readable "Could not load imported files (…)" message. Start MySQL, switch tabs and back: the table loads again.

## Batch Generate (pending since changelog v1.8)

- [ ] Type `0`, `1001`, `abc`, and an empty value into Number of sentences, then press Generate. Each shows "Number of sentences must be a whole number from 1 to 1000." and nothing is generated.
- [ ] Stop MySQL, press Generate. The status shows "Could not generate: …" or "Saved 0 of N sentences. …", and Generate, the count, and the algorithm box are usable again.
- [ ] Partial failure: generate 1000 sentences and stop MySQL while "Generating and saving…" is shown. The status reads "Saved X of 1000 sentences. …" and the list shows only the X saved rows. Sentence History has exactly those X new rows.
- [ ] Select some results and press Add selected. They show `[Added to corpus]`. Select only those rows: Add selected stays disabled. Select a mix: only the new ones are added (the status count matches).

## Autocomplete (pending since changelog v1.9 and v2.0)

- [ ] Type a word longer than 100 characters followed by `.`. The status names the 100-character limit with a preview of the word, and that sentence is not saved.
- [ ] Type `the cat sat - on the mat.` and check Words Report for a word that is just `-`. Failed on v2.0 (stored `-` with 0/0/0 counts); fixed on `main` by PR #11. To re-check, type it again, then type `sat` with a space: `on` is suggested and `-` (if listed from the old run) keeps its old typed count. An existing `-` row stays, because words are never deleted.
- [ ] End to end (v2.0 pending item): type a sentence, pick two suggestions, end with `.`. Words Report shows any new words, and suggestions for the same words now show the higher typed count.
