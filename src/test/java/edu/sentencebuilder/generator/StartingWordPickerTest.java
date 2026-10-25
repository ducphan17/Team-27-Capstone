//Aalvee
package edu.sentencebuilder.generator;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import edu.sentencebuilder.model.Word;

class StartingWordPickerTest {
    private static Word word(String text, int starts) {
        return new Word(0, text, starts, starts, 0, null);
    }

    private static Random ticket(long value, long expectedBound) {
        // Check the full weight as well as the chosen word: no percentage tolerances.
        return new Random() {
            @Override
            public long nextLong(long bound) {
                assertEquals(expectedBound, bound);
                assertTrue(value >= 0 && value < bound);
                return value;
            }
        };
    }

    @Test
    void weightsThreeAndOneHaveExactTicketBoundaries() {
        List<Word> words = List.of(word("first", 3), word("second", 1));
        for (long value = 0; value < 3; value++) {
            assertEquals("first", StartingWordPicker.pick(words, ticket(value, 4)));
        }
        assertEquals("second", StartingWordPicker.pick(words, ticket(3, 4)));
    }

    @Test
    void zeroCountWordsReceiveNoTickets() {
        List<Word> words = List.of(word("zero-before", 0), word("first", 3),
                word("zero-between", 0), word("second", 1), word("zero-after", 0));
        for (long value = 0; value < 4; value++) {
            assertEquals(value < 3 ? "first" : "second",
                    StartingWordPicker.pick(words, ticket(value, 4)));
        }
    }

    @Test
    void emptyListExplainsThatTextMustBeImported() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> StartingWordPicker.pick(List.of(), new Random()));
        assertTrue(error.getMessage().contains("No starting words"));
        assertTrue(error.getMessage().contains("Import some text first"));
    }

    @Test
    void totalWeightCanExceedIntegerRange() {
        List<Word> words = List.of(word("first", Integer.MAX_VALUE), word("second", 1));
        long total = (long) Integer.MAX_VALUE + 1;
        assertEquals("first", StartingWordPicker.pick(words, ticket(0, total)));
        assertEquals("first", StartingWordPicker.pick(words,
                ticket((long) Integer.MAX_VALUE - 1, total)));
        assertEquals("second", StartingWordPicker.pick(words,
                ticket(Integer.MAX_VALUE, total)));
    }
}
