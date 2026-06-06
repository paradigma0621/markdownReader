package com.markdownreader.markdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeadingTest {

    @Test
    void constructorAndAccessors() {
        Heading h = new Heading(2, "Section Title", "section-title");
        assertEquals(2, h.level());
        assertEquals("Section Title", h.text());
        assertEquals("section-title", h.id());
    }

    @Test
    void equalityAndHashCode() {
        Heading h1 = new Heading(1, "Title", "title");
        Heading h2 = new Heading(1, "Title", "title");
        assertEquals(h1, h2);
        assertEquals(h1.hashCode(), h2.hashCode());
    }

    @Test
    void inequalityWhenLevelDiffers() {
        Heading h1 = new Heading(1, "Title", "title");
        Heading h2 = new Heading(2, "Title", "title");
        assertNotEquals(h1, h2);
    }

    @Test
    void inequalityWhenTextDiffers() {
        Heading h1 = new Heading(1, "Alpha", "alpha");
        Heading h2 = new Heading(1, "Beta", "alpha");
        assertNotEquals(h1, h2);
    }

    @Test
    void inequalityWhenIdDiffers() {
        Heading h1 = new Heading(1, "Title", "id-1");
        Heading h2 = new Heading(1, "Title", "id-2");
        assertNotEquals(h1, h2);
    }

    @Test
    void toStringContainsFields() {
        Heading h = new Heading(3, "Hello", "hello");
        String s = h.toString();
        assertTrue(s.contains("3"));
        assertTrue(s.contains("Hello"));
        assertTrue(s.contains("hello"));
    }

    @Test
    void allHeadingLevels() {
        for (int level = 1; level <= 6; level++) {
            Heading h = new Heading(level, "Text", "text");
            assertEquals(level, h.level());
        }
    }
}
