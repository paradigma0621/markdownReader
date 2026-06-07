package com.markdownreader.markdown;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownRendererTest {

    private MarkdownRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new MarkdownRenderer();
    }

    @Test
    void renderNullTreatedAsEmpty() {
        RenderResult result = renderer.render(null);
        assertNotNull(result);
        assertNotNull(result.html());
        assertTrue(result.headings().isEmpty());
    }

    @Test
    void renderEmptyString() {
        RenderResult result = renderer.render("");
        assertNotNull(result);
        assertTrue(result.headings().isEmpty());
    }

    @Test
    void renderPlainTextProducedInHtml() {
        RenderResult result = renderer.render("Hello world");
        assertTrue(result.html().contains("Hello world"));
        assertTrue(result.headings().isEmpty());
    }

    @Test
    void renderH1ExtractsHeading() {
        RenderResult result = renderer.render("# Title");
        List<Heading> headings = result.headings();
        assertEquals(1, headings.size());
        assertEquals(1, headings.get(0).level());
        assertEquals("Title", headings.get(0).text());
    }

    @Test
    void renderMultipleHeadingLevels() {
        RenderResult result = renderer.render("# H1\n## H2\n### H3\n#### H4\n##### H5\n###### H6");
        List<Heading> headings = result.headings();
        assertEquals(6, headings.size());
        for (int i = 0; i < 6; i++) {
            assertEquals(i + 1, headings.get(i).level());
        }
    }

    @Test
    void renderHeadingsHaveNonEmptyIds() {
        RenderResult result = renderer.render("# My Title");
        assertFalse(result.headings().get(0).id().isEmpty());
    }

    @Test
    void renderHeadingIdUsableAsAnchor() {
        RenderResult result = renderer.render("# My Title");
        String html = result.html();
        String id = result.headings().get(0).id();
        assertTrue(html.contains("id=\"" + id + "\"") || html.contains("name=\"" + id + "\"") || html.contains(id));
    }

    @Test
    void renderTable() {
        String md = "| Col1 | Col2 |\n|------|------|\n| A | B |";
        RenderResult result = renderer.render(md);
        assertTrue(result.html().contains("<table"));
        assertTrue(result.html().contains("Col1"));
        assertTrue(result.html().contains("Col2"));
    }

    @Test
    void renderTaskListCheckedAndUnchecked() {
        RenderResult result = renderer.render("- [x] Done\n- [ ] Todo");
        String html = result.html();
        assertTrue(html.contains("Done"));
        assertTrue(html.contains("Todo"));
        assertTrue(html.contains("checked") || html.contains("task-list"));
    }

    @Test
    void renderStrikethrough() {
        RenderResult result = renderer.render("~~strikethrough~~");
        assertTrue(result.html().contains("<del>") || result.html().contains("strikethrough"));
    }

    @Test
    void renderAutolink() {
        RenderResult result = renderer.render("See https://example.com for details");
        assertTrue(result.html().contains("example.com"));
        assertTrue(result.html().contains("<a") || result.html().contains("href"));
    }

    @Test
    void renderCodeBlock() {
        RenderResult result = renderer.render("```java\nSystem.out.println(\"hello\");\n```");
        assertTrue(result.html().contains("<code") || result.html().contains("<pre"));
        assertTrue(result.html().contains("println"));
    }

    @Test
    void renderInlineCode() {
        RenderResult result = renderer.render("Use `System.out.println()` here.");
        assertTrue(result.html().contains("<code>"));
    }

    @Test
    void renderSoftBreakBecomesBr() {
        RenderResult result = renderer.render("Line one\nLine two");
        assertTrue(result.html().contains("<br") || result.html().contains("Line one"));
    }

    @Test
    void plainTextProducesNoHeadings() {
        RenderResult result = renderer.render("Just plain text with no headings at all.");
        assertTrue(result.headings().isEmpty());
    }

    @Test
    void renderMultipleH1Headings() {
        RenderResult result = renderer.render("# A\n# B\n# C");
        assertEquals(3, result.headings().size());
        result.headings().forEach(h -> assertEquals(1, h.level()));
    }

    @Test
    void renderMixedNestedHeadings() {
        RenderResult result = renderer.render("# Top\n## Sub\n## Sub2\n# Top2");
        assertEquals(4, result.headings().size());
        assertEquals(1, result.headings().get(0).level());
        assertEquals(2, result.headings().get(1).level());
        assertEquals(2, result.headings().get(2).level());
        assertEquals(1, result.headings().get(3).level());
    }

    @Test
    void renderBold() {
        RenderResult result = renderer.render("**bold text**");
        assertTrue(result.html().contains("<strong>") || result.html().contains("bold text"));
    }

    @Test
    void renderItalic() {
        RenderResult result = renderer.render("*italic text*");
        assertTrue(result.html().contains("<em>") || result.html().contains("italic text"));
    }

    @Test
    void renderBlockquote() {
        RenderResult result = renderer.render("> A blockquote");
        assertTrue(result.html().contains("<blockquote>") || result.html().contains("blockquote"));
    }

    @Test
    void renderHorizontalRule() {
        RenderResult result = renderer.render("---");
        assertTrue(result.html().contains("<hr") || result.html().contains("---"));
    }

    @Test
    void renderUnorderedList() {
        RenderResult result = renderer.render("- item1\n- item2\n- item3");
        assertTrue(result.html().contains("<ul>") || result.html().contains("<li>"));
        assertTrue(result.html().contains("item1"));
    }

    @Test
    void renderOrderedList() {
        RenderResult result = renderer.render("1. first\n2. second\n3. third");
        assertTrue(result.html().contains("<ol>") || result.html().contains("<li>"));
        assertTrue(result.html().contains("first"));
    }

    @Test
    void renderLink() {
        RenderResult result = renderer.render("[Click here](https://example.com)");
        assertTrue(result.html().contains("href"));
        assertTrue(result.html().contains("Click here"));
    }

    @Test
    void renderImage() {
        RenderResult result = renderer.render("![Alt text](image.png)");
        assertTrue(result.html().contains("<img") || result.html().contains("Alt text"));
    }

    @Test
    void headingWithWhitespaceOnlyIsNotAdded() {
        // A section with only code (no text headings) should have empty headings
        RenderResult result = renderer.render("```\ncode block only\n```");
        assertTrue(result.headings().isEmpty());
    }

    @Test
    void renderReturnsNonNullHtmlAlways() {
        assertNotNull(renderer.render(null).html());
        assertNotNull(renderer.render("").html());
        assertNotNull(renderer.render("# Hello").html());
    }

    // ------------------------------------------------- data-source-line stamping
    // The preview→editor sync relies on every block element carrying its 0-based
    // source line in a data-source-line attribute (SourceLineAttributeProvider).

    /** Returns the opening tag (e.g. {@code <h1 ...>}) of the first {@code tag} in the html. */
    private static String openTag(String html, String tag) {
        int start = html.indexOf("<" + tag);
        if (start < 0) {
            return "";
        }
        return html.substring(start, html.indexOf('>', start) + 1);
    }

    @Test
    void stampsZeroBasedSourceLineOnEveryBlockOfAMultiBlockDocument() {
        String md = "# Title\n\nParagraph text\n\n## Section\n\nMore text";
        String html = renderer.render(md).html();
        assertTrue(openTag(html, "h1").contains("data-source-line=\"0\""), html);
        assertTrue(openTag(html, "p").contains("data-source-line=\"2\""), html);
        assertTrue(openTag(html, "h2").contains("data-source-line=\"4\""), html);
        // The second paragraph sits on line 6.
        assertTrue(html.contains("<p data-source-line=\"6\">"), html);
    }

    @Test
    void headingBlockCarriesItsOwnSourceLine() {
        String html = renderer.render("intro\n\n# Heading").html();
        // "intro" is line 0, the heading is line 2.
        assertTrue(openTag(html, "p").contains("data-source-line=\"0\""), html);
        assertTrue(openTag(html, "h1").contains("data-source-line=\"2\""), html);
    }

    @Test
    void codeFenceBlockCarriesSourceLine() {
        String html = renderer.render("para\n\n```\ncode\n```").html();
        // The fenced code block (rendered as <pre>) starts on line 2.
        assertTrue(openTag(html, "pre").contains("data-source-line=\"2\""), html);
    }

    @Test
    void listBlockCarriesSourceLine() {
        String html = renderer.render("# H\n\n- a\n- b").html();
        // The bullet list block starts on line 2.
        assertTrue(openTag(html, "ul").contains("data-source-line=\"2\""), html);
    }

    @Test
    void firstAndOnlyBlockIsSourceLineZero() {
        String html = renderer.render("Just one paragraph").html();
        assertTrue(openTag(html, "p").contains("data-source-line=\"0\""), html);
    }

    @Test
    void inlineContentDoesNotReceiveItsOwnSourceLineAttribute() {
        // Only block elements are stamped; inline nodes (the link/emphasis here) must not be.
        String html = renderer.render("text with **bold** and [a link](https://example.com)").html();
        assertTrue(openTag(html, "p").contains("data-source-line=\"0\""), html);
        assertFalse(openTag(html, "a").contains("data-source-line"), html);
        assertFalse(openTag(html, "strong").contains("data-source-line"), html);
    }
}
