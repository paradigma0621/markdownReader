package com.markdownreader;

import com.markdownreader.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.File;
import java.io.InputStream;

/**
 * JavaFX application — styled Markdown document viewer.
 */
public class App extends Application {

    private static final int MIN_WIDTH = 720;
    private static final int MIN_HEIGHT = 480;

    @Override
    public void start(Stage stage) {
        MainView mainView = new MainView(stage);

        Scene scene = new Scene(mainView.getRoot(), 1180, 800);
        scene.getStylesheets().add(resource("/css/app.css"));

        stage.setScene(scene);
        stage.setTitle("Markdown Reader");
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        loadIcon(stage);
        stage.show();

        // Opens file passed via command line, if any.
        var args = getParameters().getRaw();
        if (!args.isEmpty()) {
            File file = new File(args.get(0));
            if (file.isFile()) {
                mainView.openFile(file);
            }
        }

        mainView.requestFocus();
    }

    private void loadIcon(Stage stage) {
        try (InputStream in = App.class.getResourceAsStream("/icons/app-icon.png")) {
            if (in != null) {
                stage.getIcons().add(new Image(in));
            }
        } catch (Exception ignored) {
            // Icon is optional.
        }
    }

    private static String resource(String path) {
        var url = App.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("Resource not found: " + path);
        }
        return url.toExternalForm();
    }
}
