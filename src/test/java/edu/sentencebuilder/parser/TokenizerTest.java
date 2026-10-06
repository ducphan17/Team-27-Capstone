package edu.sentencebuilder.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
