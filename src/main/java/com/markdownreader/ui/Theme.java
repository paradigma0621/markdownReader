package com.markdownreader.ui;

/** Temas visuais suportados pelo apresentador. */
public enum Theme {
    LIGHT,
    DARK;

    /** Retorna o tema oposto ao atual. */
    public Theme toggled() {
        return this == LIGHT ? DARK : LIGHT;
    }
}
