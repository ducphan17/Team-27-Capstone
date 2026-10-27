// Tien Phan

package edu.sentencebuilder.ui;

import edu.sentencebuilder.parser.Tokenizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps Autocomplete sentences whose save is not confirmed yet in a small text file, one
 * sentence per line, so closing the app (or a crash) while the database is unreachable does
 * not lose them. The file lives in the working directory, is git-ignored, and is deleted
 * once nothing is pending. Must only be called from background threads.
 */
final class PendingSentences {

    /** Where the app keeps pending sentences, next to {@code db.properties}. */
    static final Path FILE = Path.of("autocomplete-pending.txt");

    private static final Tokenizer TOKENIZER = new Tokenizer();

    private PendingSentences() {
    }

    /** Reads pending sentences; an absent file means none. */
    static List<List<String>> read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        List<List<String>> sentences = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            // Re-tokenize so a hand-edited line still yields normalized words.
            sentences.addAll(TOKENIZER.tokenize(line));
        }
        return sentences;
    }

    /** Replaces the file with {@code sentences}, or deletes it when there are none. */
    static void write(Path file, List<List<String>> sentences) throws IOException {
        if (sentences.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        List<String> lines = sentences.stream().map(words -> String.join(" ", words)).toList();
        // Write a temporary file first, so a crash mid-write never leaves a half-written list.
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
