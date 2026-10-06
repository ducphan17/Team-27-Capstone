package edu.sentencebuilder.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX entry point: loads {@code main.fxml} (a TabPane whose tabs are
 * {@code fx:include}d from their own FXML files) and shows the window.
 * Start the app through {@link Launcher}, not this class, when running from an IDE.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(MainApp.class.getResource("main.fxml"));
        stage.setTitle("SentenceBuilder");
        stage.setScene(new Scene(root, 900, 600));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
