package edu.sentencebuilder.ui;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Runs database work off the JavaFX Application Thread. Wraps a {@link Callable}
 * in a {@link Task} on a daemon thread; the success and failure callbacks run on
 * the FX thread, so they may touch controls.
 */
final class Background {

    private Background() {
    }

    /**
     * Starts {@code work} on a new daemon thread named {@code threadName}.
     *
     * @return the running task, e.g. to bind a control's disable property to it
     */
    static <T> Task<T> run(String threadName, Callable<T> work,
                           Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = task(work, onSuccess, onFailure);
        Thread worker = new Thread(task, threadName);
        worker.setDaemon(true); // don't keep the JVM alive after the window closes
        worker.start();
        return task;
    }

    /**
     * Runs {@code work} on {@code executor} instead of a new thread, e.g. a single-thread
     * executor that keeps writes in order. Callbacks run on the FX thread, as above.
     */
    static <T> Task<T> run(Executor executor, Callable<T> work,
                           Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = task(work, onSuccess, onFailure);
        executor.execute(task);
        return task;
    }

    private static <T> Task<T> task(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> onFailure.accept(task.getException()));
        return task;
    }

    /**
     * A short message for a failed task, safe to show in a label: the error's own message
     * plus its root cause, e.g. "Could not load words (Communications link failure)".
     */
    static String describe(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = messageOf(error);
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        // DataAccessException wraps the SQLException that says what actually went wrong.
        return root == error ? message : message + " (" + messageOf(root) + ")";
    }

    private static String messageOf(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
