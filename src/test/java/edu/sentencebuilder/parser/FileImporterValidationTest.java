package edu.sentencebuilder.parser;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

import edu.sentencebuilder.dao.FileDAO;
import edu.sentencebuilder.dao.WordDAO;
import edu.sentencebuilder.dao.WordPairDAO;

class FileImporterValidationTest {
    // Use real tokenization and counting, as the background import does.
    static TextImporter.Stats count(String text) {
        TextImporter.Stats stats = new TextImporter.Stats();
        new Tokenizer().tokenize(text).forEach(stats::addSentence);
        return stats;
    }

    @Test
    void acceptsExactlyOneHundredCharacters() {
        assertDoesNotThrow(() -> TextImporter.validateWordLengths(count("a".repeat(100))));
    }

    @Test
    void countsSupplementaryUnicodeLettersAsSingleCharacters() {
        String letter = new String(Character.toChars(0x10428));
        assertDoesNotThrow(() -> TextImporter.validateWordLengths(count(letter.repeat(100))));
        assertThrows(InvalidImportException.class,
                () -> TextImporter.validateWordLengths(count(letter.repeat(101))));
    }

    @Test
    void rejectsTheWholeImportBeforeOpeningAConnection() {
        WordDAO words = new WordDAO();
        FileImporter importer = new FileImporter(Path.of("oversized.txt"), new Tokenizer(),
                words, new WordPairDAO(words), new FileDAO(), () -> {
                    fail("An invalid import must not even open a database connection");
                    return null;
                });
        InvalidImportException error = assertThrows(InvalidImportException.class,
                () -> importer.writeToDatabase(count("Valid words first. " + "a".repeat(101)), 2));
        assertTrue(error.getMessage().contains("100-character limit (101 characters)"));
        assertTrue(error.getMessage().contains("No data was imported."));
        assertFalse(error.getMessage().contains("a".repeat(101)), "Keep the error preview short");
    }

    //Aalvee
    @Test
    void textImportRejectsOversizedWordsBeforeUsingTheConnection() {
        WordDAO words = new WordDAO();
        FileImporter importer = new FileImporter(Path.of("unused.txt"), new Tokenizer(),
                words, new WordPairDAO(words), new FileDAO());

        // A null connection ensures validation happens before any database access.
        InvalidImportException error = assertThrows(InvalidImportException.class,
                () -> importer.importText(null,
                        "Ordinary words. " + "a".repeat(101) + ".", "batch-generated"));
        assertTrue(error.getMessage().contains("No data was imported."));
    }

    @Test
    void textImportRejectsInputWithoutWords() {
        WordDAO words = new WordDAO();
        FileImporter importer = new FileImporter(Path.of("unused.txt"), new Tokenizer(),
                words, new WordPairDAO(words), new FileDAO());

        InvalidImportException error = assertThrows(InvalidImportException.class,
                () -> importer.importText(null, "... ! ?", "batch-generated"));
        assertEquals("There are no words to add to the corpus.", error.getMessage());
    }
}
