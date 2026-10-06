package edu.sentencebuilder.dao;

/**
 * Unchecked wrapper for {@link java.sql.SQLException}, used where an interface
 * method (such as {@link edu.sentencebuilder.generator.WordStatsSource}) cannot
 * declare checked exceptions.
 */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
