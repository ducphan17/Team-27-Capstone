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

    private static final System.Logger LOGGER = System.getLogger(MainApp.class.getName());

    @Override
    public void start(Stage stage) throws Exception {
        // Register bundled faces before CSS resolves regular and bold text.
        boolean fontsLoaded = true;
        for (String face : new String[]{"Regular", "Bold"}) {
            try {
                var resource = MainApp.class.getResource("fonts/JosefinSans-" + face + ".ttf");
                if (resource == null || Font.loadFont(resource.toExternalForm(), 14) == null) {
                    fontsLoaded = false;
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Could not load bundled Josefin Sans " + face + " font; using the system font.");
                }
            } catch (RuntimeException error) {
                fontsLoaded = false;
                LOGGER.log(System.Logger.Level.WARNING,
                        "Could not load bundled Josefin Sans " + face + " font; using the system font.", error);
            }
        }
        Parent root = FXMLLoader.load(MainApp.class.getResource("main.fxml"));
        if (!fontsLoaded) {
            root.setStyle("-fx-font-family: \"System\";");
        }
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
