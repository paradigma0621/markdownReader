package com.markdownreader.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThemeTest {

    @Test
    void lightToggledIsDark() {
        assertEquals(Theme.DARK, Theme.LIGHT.toggled());
    }

    @Test
    void darkToggledIsLight() {
        assertEquals(Theme.LIGHT, Theme.DARK.toggled());
    }

    @Test
    void valuesHasTwoEntries() {
        assertEquals(2, Theme.values().length);
    }

    @Test
    void valueOfLight() {
        assertEquals(Theme.LIGHT, Theme.valueOf("LIGHT"));
    }

    @Test
    void valueOfDark() {
        assertEquals(Theme.DARK, Theme.valueOf("DARK"));
    }

    @Test
    void toggledIsIdempotentRoundTrip() {
        assertEquals(Theme.LIGHT, Theme.LIGHT.toggled().toggled());
        assertEquals(Theme.DARK, Theme.DARK.toggled().toggled());
    }
}
