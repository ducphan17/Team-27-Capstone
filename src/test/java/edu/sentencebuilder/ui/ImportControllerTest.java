package edu.sentencebuilder.ui;

import edu.sentencebuilder.model.SourceFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImportControllerTest {

    @Test
    void summaryForEmptyHistory() {
        assertEquals("No imports yet.", ImportController.historySummary(List.of()));
    }

    @Test
    void summaryUsesSingularForOneImportAndOneWord() {
        assertEquals("1 import, 1 word in total",
                ImportController.historySummary(List.of(new SourceFile("one.txt", 1, 1))));
    }

    @Test
    void summaryAddsWordCountsAcrossImports() {
        List<SourceFile> files = List.of(
                new SourceFile("a.txt", 1200, 80),
                new SourceFile("batch-generated", 50, 5));
        assertEquals("2 imports, 1250 words in total", ImportController.historySummary(files));
    }
}
