package com.markdownreader.markdown;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RenderResultTest {

    @Test
    void html() {
        RenderResult result = new RenderResult("<p>Hello</p>", List.of());
        assertEquals("<p>Hello</p>", result.html());
    }

    @Test
    void headings() {
        List<Heading> headings = List.of(new Heading(1, "Title", "title"));
        RenderResult result = new RenderResult("<p>Hello</p>", headings);
        assertEquals(headings, result.headings());
    }

    @Test
    void emptyHeadings() {
        RenderResult result = new RenderResult("", List.of());
        assertTrue(result.headings().isEmpty());
    }

    @Test
    void equalityAndHashCode() {
        List<Heading> h = List.of(new Heading(1, "T", "t"));
        RenderResult r1 = new RenderResult("<p>A</p>", h);
        RenderResult r2 = new RenderResult("<p>A</p>", h);
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void inequality() {
        RenderResult r1 = new RenderResult("<p>A</p>", List.of());
        RenderResult r2 = new RenderResult("<p>B</p>", List.of());
        assertNotEquals(r1, r2);
    }

    @Test
    void toStringContainsHtml() {
        RenderResult result = new RenderResult("<p>Hello</p>", List.of());
        assertTrue(result.toString().contains("Hello"));
    }
}
