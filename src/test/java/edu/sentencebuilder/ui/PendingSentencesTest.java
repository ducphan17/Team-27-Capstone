// Tien Phan

package edu.sentencebuilder.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PendingSentencesTest {

    @TempDir
    Path dir;

    @Test
    void sentencesSurviveAWriteAndRead() throws IOException {
        Path file = dir.resolve("pending.txt");
        List<List<String>> sentences = List.of(List.of("the", "cat"), List.of("don't", "stop"), List.of("the", "cat"));
        PendingSentences.write(file, sentences);
        assertEquals(sentences, PendingSentences.read(file));
    }

    @Test
    void writingNothingDeletesTheFile() throws IOException {
        Path file = dir.resolve("pending.txt");
        PendingSentences.write(file, List.of(List.of("the", "cat")));
        PendingSentences.write(file, List.of());
        assertFalse(Files.exists(file));
        assertFalse(Files.exists(dir.resolve("pending.txt.tmp")));
    }

    @Test
    void missingFileMeansNothingIsPending() throws IOException {
        assertEquals(List.of(), PendingSentences.read(dir.resolve("absent.txt")));
    }

    @Test
    void handEditedLinesAreNormalized() throws IOException {
        Path file = dir.resolve("pending.txt");
        Files.writeString(file, "The Cat, sat\n\n  \nIt RAN\n");
        assertEquals(List.of(List.of("the", "cat", "sat"), List.of("it", "ran")), PendingSentences.read(file));
    }
}
