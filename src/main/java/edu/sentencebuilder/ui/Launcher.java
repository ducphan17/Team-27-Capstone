package edu.sentencebuilder.ui;

/**
 * Plain main class that does not extend {@code Application}.
 * When the main class extends Application and JavaFX is on the classpath (not the
 * module path), the JVM refuses to start with "JavaFX runtime components are missing".
 * Launching through this class avoids that check, so the app runs from any IDE.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        MainApp.main(args);
    }
}
