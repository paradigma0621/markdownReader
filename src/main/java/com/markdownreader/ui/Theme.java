package com.markdownreader.ui;

/** Visual themes supported by the viewer. */
public enum Theme {
    LIGHT,
    DARK;

    /** Returns the opposite of the current theme. */
    public Theme toggled() {
        return this == LIGHT ? DARK : LIGHT;
    }
}
