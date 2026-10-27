package edu.sentencebuilder.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class TokenizerTest {

    private final Tokenizer tokenizer = new Tokenizer();

    @Test
    void lowercasesAllWords() {
        assertEquals(List.of(List.of("the", "quick", "fox")), tokenizer.tokenize("The QUICK Fox."));
    }

    @Test
    void splitsSentencesOnPeriodExclamationAndQuestionMark() {
        List<List<String>> result = tokenizer.tokenize("One two. Three four! Five six? Seven");
        assertEquals(List.of(
                List.of("one", "two"),
                List.of("three", "four"),
                List.of("five", "six"),
                List.of("seven")), result);
    }

    @Test
    void repeatedEndMarksDoNotCreateEmptySentences() {
        assertEquals(List.of(List.of("what"), List.of("really")), tokenizer.tokenize("What?! Really..."));
    }

    @Test
    void keepsApostrophesInsideWords() {
        assertEquals(List.of(List.of("don't", "worry", "it's", "fine")), tokenizer.tokenize("Don't worry, it's fine."));
    }

    @Test
    void normalizesCurlyApostrophes() {
        assertEquals(List.of(List.of("don't")), tokenizer.tokenize("Don’t."));
    }

    @Test
    void stripsOtherPunctuation() {
        List<List<String>> result = tokenizer.tokenize("Well, \"hello\"; (there): 'friend'.");
        assertEquals(List.of(List.of("well", "hello", "there", "friend")), result);
    }

    @Test
    void emptyInputReturnsNoSentences() {
        assertTrue(tokenizer.tokenize("").isEmpty());
        assertTrue(tokenizer.tokenize(null).isEmpty());
    }

    @Test
    void whitespaceAndPunctuationOnlyInputReturnsNoSentences() {
        assertTrue(tokenizer.tokenize("   \n\t  ").isEmpty());
        assertTrue(tokenizer.tokenize(" ... !? , ").isEmpty());
    }

    @Test
    void testTrailAndLeadingPunctuation() {
        // Ensures punctuation attached to a word is stripped cleanly
        String input = "\"Wait,\" she said. (Really?)";
        List<List<String>> expected = List.of(
                List.of("wait", "she", "said"),
                List.of("really")
        );
        assertIterableEquals(expected, tokenizer.tokenize(input));
    }

    @Test
    void testContractionsAreKeptIntact() {
        // Ensures the apostrophe doesn't split the word into "don" and "t"
        String input = "Don't say it's impossible.";
        List<List<String>> expected = List.of(
                List.of("don't", "say", "it's", "impossible")
        );
        assertIterableEquals(expected, tokenizer.tokenize(input));
    }

    @Test
    void testHyphenatedWords() {
        // Ensures compound words remain as single tokens
        String input = "The state-of-the-art sub-system.";
        List<List<String>> expected = List.of(
                List.of("the", "state-of-the-art", "sub-system")
        );
        assertIterableEquals(expected, tokenizer.tokenize(input));
    }

    @Test
    void testMultipleWhitespacesAndNewLines() {
        // Ensures tabs, newlines, and multiple spaces don't create empty string tokens
        String input = "One \t two \n\n three.";
        List<List<String>> expected = List.of(
                List.of("one", "two", "three")
        );
        assertIterableEquals(expected, tokenizer.tokenize(input));
    }

    @Test
    void testAlphanumericBoundaries() {
        // Ensures version numbers or numeric values tokenize correctly
        String input = "Version 1.8 has 52 tests.";
        List<List<String>> expected = List.of(
                List.of("version", "1.8", "has", "52", "tests")
        );
        assertIterableEquals(expected, tokenizer.tokenize(input));
    }
}
