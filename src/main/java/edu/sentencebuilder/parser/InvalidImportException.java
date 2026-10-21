package edu.sentencebuilder.parser;

/** Invalid import input, distinct from SQL failures and unexpected runtime errors. */
public class InvalidImportException extends IllegalArgumentException {
    public InvalidImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
