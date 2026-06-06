package com.markdownreader.markdown;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RenderResultTest {

    @Test
    void constructorAndAccessors() {
        List<Heading> headings = List.of(new Heading(1, "Title", "title"));
        RenderResult result = new RenderResult("<p>Hello</p>", headings);
        assertEquals("<p>Hello</p>", result.html());
        assertEquals(headings, result.headings());
    }

    @Test
    void emptyHeadingsAndHtml() {
        RenderResult result = new RenderResult("", List.of());
        assertTrue(result.headings().isEmpty());
        assertEquals("", result.html());
    }

    @Test
    void equality() {
        List<Heading> h = List.of(new Heading(2, "Test", "test"));
        RenderResult r1 = new RenderResult("<p>x</p>", h);
        RenderResult r2 = new RenderResult("<p>x</p>", h);
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void inequalityWhenHtmlDiffers() {
        List<Heading> h = List.of();
        RenderResult r1 = new RenderResult("<p>a</p>", h);
        RenderResult r2 = new RenderResult("<p>b</p>", h);
        assertNotEquals(r1, r2);
    }

    @Test
    void inequalityWhenHeadingsDiffer() {
        RenderResult r1 = new RenderResult("<p>x</p>", List.of(new Heading(1, "A", "a")));
        RenderResult r2 = new RenderResult("<p>x</p>", List.of(new Heading(2, "B", "b")));
        assertNotEquals(r1, r2);
    }

    @Test
    void toStringContainsFields() {
        RenderResult r = new RenderResult("<p>hello</p>", List.of());
        String s = r.toString();
        assertNotNull(s);
        assertFalse(s.isBlank());
    }

    @Test
    void multipleHeadings() {
        List<Heading> headings = List.of(
                new Heading(1, "H1", "h1"),
                new Heading(2, "H2", "h2"),
                new Heading(3, "H3", "h3")
        );
        RenderResult result = new RenderResult("<h1>H1</h1>", headings);
        assertEquals(3, result.headings().size());
    }
}
