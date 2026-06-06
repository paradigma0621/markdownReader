package com.markdownreader;

import javafx.application.Application;

/**
 * Ponto de entrada "puro" (não estende {@link Application}).
 * <p>
 * Necessário para que a aplicação JavaFX rode tanto a partir do JAR "fat"
 * (shade) quanto pelo classpath sem o erro
 * "JavaFX runtime components are missing".
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
