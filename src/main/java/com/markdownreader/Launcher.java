package com.markdownreader;

import javafx.application.Application;

/**
 * "Pure" entry point (does not extend {@link Application}).
 * <p>
 * Required so that the JavaFX application runs both from the "fat" JAR
 * (shade) and from the classpath without the error
 * "JavaFX runtime components are missing".
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
