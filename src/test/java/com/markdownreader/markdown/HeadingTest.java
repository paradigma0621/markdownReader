package com.markdownreader.markdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeadingTest {

    @Test
    void level() {
        assertEquals(2, new Heading(2, "Introduction", "introduction").level());
    }

    @Test
    void text() {
        assertEquals("Introduction", new Heading(2, "Introduction", "introduction").text());
    }

    @Test
    void id() {
        assertEquals("introduction", new Heading(2, "Introduction", "introduction").id());
    }

    @Test
    void equalityAndHashCode() {
        Heading h1 = new Heading(1, "Title", "title");
        Heading h2 = new Heading(1, "Title", "title");
        assertEquals(h1, h2);
        assertEquals(h1.hashCode(), h2.hashCode());
    }

    @Test
    void inequalityOnLevel() {
        assertNotEquals(new Heading(1, "T", "t"), new Heading(2, "T", "t"));
    }

    @Test
    void inequalityOnText() {
        assertNotEquals(new Heading(1, "A", "t"), new Heading(1, "B", "t"));
    }

    @Test
    void inequalityOnId() {
        assertNotEquals(new Heading(1, "T", "a"), new Heading(1, "T", "b"));
    }

    @Test
    void toStringContainsFields() {
        Heading h = new Heading(3, "Section", "section");
        String s = h.toString();
        assertTrue(s.contains("3"));
        assertTrue(s.contains("Section"));
        assertTrue(s.contains("section"));
    }
}
