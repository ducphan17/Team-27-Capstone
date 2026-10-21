package edu.sentencebuilder.parser;

import edu.sentencebuilder.model.WordPair;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FileImporterStatsTest {
    @Test
    void resolvesIdsAndAggregatesRepeatedPairs() throws SQLException {
        FileImporter.Stats stats = new FileImporter.Stats();
        stats.addSentence(List.of("zorvex", "miplex"));
        stats.addSentence(List.of("zorvex", "miplex"));
        stats.addSentence(List.of("zorvex", "talvex"));
        List<WordPair> pairs = stats.toWordPairs(Map.of("zorvex", 17, "miplex", 42, "talvex", 93));

        // HashMap traversal order is unspecified, so compare the complete set of records.
        assertEquals(2, pairs.size());
        assertEquals(Set.of(List.of(17, 42, 2, 0), List.of(17, 93, 1, 0)),
                pairs.stream().map(p -> List.of(p.getFromWordId(), p.getToWordId(),
                        p.getCount(), p.getUserCount())).collect(Collectors.toSet()));
    }

    @Test
    void emptyStatisticsHaveNoPairs() throws SQLException {
        assertTrue(new FileImporter.Stats().toWordPairs(Map.of()).isEmpty());
    }

    @Test
    void singleWordSentenceNeedsNoPairIds() throws SQLException {
        FileImporter.Stats stats = new FileImporter.Stats();
        stats.addSentence(List.of("alone"));
        assertTrue(stats.toWordPairs(Map.of()).isEmpty());
    }

    @Test
    void missingSourceIdNamesTheWord() {
        FileImporter.Stats stats = new FileImporter.Stats();
        stats.addSentence(List.of("zorvex", "miplex"));
        SQLException error = assertThrows(SQLException.class,
                () -> stats.toWordPairs(Map.of("miplex", 42)));
        assertEquals("Missing word ID: zorvex", error.getMessage());
    }

    @Test
    void missingDestinationIdNamesTheWord() {
        FileImporter.Stats stats = new FileImporter.Stats();
        stats.addSentence(List.of("zorvex", "miplex"));
        SQLException error = assertThrows(SQLException.class,
                () -> stats.toWordPairs(Map.of("zorvex", 17)));
        assertEquals("Missing word ID: miplex", error.getMessage());
    }
}
