package edu.sentencebuilder.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * JavaFX entry point: loads {@code main.fxml} (a workspace whose tabs are
 * {@code fx:include}d from their own FXML files) and shows the window.
 * Start the app through {@link Launcher}, not this class, when running from an IDE.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // Register bundled faces before CSS resolves regular and bold text.
        for (String face : new String[]{"Regular", "Bold"}) {
            var resource = MainApp.class.getResource("fonts/JosefinSans-" + face + ".ttf");
            if (resource == null || Font.loadFont(resource.toExternalForm(), 14) == null) {
                throw new IllegalStateException("Could not load bundled Josefin Sans " + face + " font.");
            }
        }
        Parent root = FXMLLoader.load(MainApp.class.getResource("main.fxml"));
        stage.setTitle("SentenceBuilder");
        stage.setScene(new Scene(root, 1120, 740));
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
