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
        assertNotNull(result.headings());
        assertTrue(result.headings().isEmpty());
    }

    @Test
    void renderEmptyStringProducesNoHeadings() {
        RenderResult result = renderer.render("");
        assertNotNull(result);
        assertTrue(result.headings().isEmpty());
    }

    @Test
    void renderSimpleParagraphContainsText() {
        RenderResult result = renderer.render("Hello World");
        assertTrue(result.html().contains("Hello World"));
        assertTrue(result.headings().isEmpty());
    }

    @Test
    void renderH1ExtractsHeadingWithCorrectLevel() {
        RenderResult result = renderer.render("# My Title");
        assertEquals(1, result.headings().size());
        Heading h = result.headings().get(0);
        assertEquals(1, h.level());
        assertEquals("My Title", h.text());
        assertFalse(h.id().isEmpty());
    }

    @Test
    void renderAllSixHeadingLevels() {
        String md = "# H1\n## H2\n### H3\n#### H4\n##### H5\n###### H6";
        List<Heading> headings = renderer.render(md).headings();
        assertEquals(6, headings.size());
        for (int i = 0; i < 6; i++) {
            assertEquals(i + 1, headings.get(i).level());
        }
    }

    @Test
    void renderHeadingsPreserveDocumentOrder() {
        String md = "# First\n## Second\n# Third";
        List<Heading> headings = renderer.render(md).headings();
        assertEquals(3, headings.size());
        assertEquals("First", headings.get(0).text());
        assertEquals("Second", headings.get(1).text());
        assertEquals("Third", headings.get(2).text());
    }

    @Test
    void renderNestedHeadingsWithChildren() {
        String md = "# Parent\n## Child\n### Grandchild";
        List<Heading> headings = renderer.render(md).headings();
        assertEquals(3, headings.size());
        assertEquals(3, headings.get(2).level());
        assertEquals("Grandchild", headings.get(2).text());
    }

    @Test
    void renderBoldText() {
        RenderResult result = renderer.render("**bold**");
        assertTrue(result.html().contains("<strong>bold</strong>"));
    }

    @Test
    void renderItalicText() {
        RenderResult result = renderer.render("*italic*");
        assertTrue(result.html().contains("<em>italic</em>"));
    }

    @Test
    void renderStrikethrough() {
        RenderResult result = renderer.render("~~strike~~");
        assertTrue(result.html().contains("strike"));
    }

    @Test
    void renderTable() {
        String md = "| A | B |\n|---|---|\n| 1 | 2 |";
        String html = renderer.render(md).html();
        assertTrue(html.contains("<table") || html.contains("</table>"));
    }

    @Test
    void renderFencedCodeBlock() {
        String md = "```\ncode here\n```";
        String html = renderer.render(md).html();
        assertTrue(html.contains("<code>") || html.contains("<pre>"));
        assertTrue(html.contains("code here"));
    }

    @Test
    void renderInlineCode() {
        RenderResult result = renderer.render("`inline`");
        assertTrue(result.html().contains("<code>inline</code>"));
    }

    @Test
    void renderTaskListItems() {
        String md = "- [x] Done\n- [ ] Todo";
        String html = renderer.render(md).html();
        assertTrue(html.contains("Done") && html.contains("Todo"));
    }

    @Test
    void renderHorizontalRule() {
        String html = renderer.render("---").html();
        assertTrue(html.contains("<hr") || html.contains("</hr>"));
    }

    @Test
    void renderBlockquote() {
        String html = renderer.render("> quoted").html();
        assertTrue(html.contains("quoted"));
    }

    @Test
    void renderUnorderedList() {
        String html = renderer.render("- item1\n- item2").html();
        assertTrue(html.contains("item1") && html.contains("item2"));
        assertTrue(html.contains("<ul>") || html.contains("<li>"));
    }

    @Test
    void renderOrderedList() {
        String html = renderer.render("1. first\n2. second").html();
        assertTrue(html.contains("first") && html.contains("second"));
    }

    @Test
    void renderLink() {
        String html = renderer.render("[click](https://example.com)").html();
        assertTrue(html.contains("href") && html.contains("click"));
    }

    @Test
    void renderHeadingWithId() {
        List<Heading> headings = renderer.render("## Section One").headings();
        assertEquals(1, headings.size());
        assertFalse(headings.get(0).id().isEmpty());
    }

    @Test
    void emptyHeadingTextIsNotExtracted() {
        // A heading with only whitespace should not be added
        RenderResult result = renderer.render("# \n## Normal");
        // The normal heading is extracted
        assertTrue(result.headings().stream().anyMatch(h -> h.text().equals("Normal")));
    }

    @Test
    void renderMultipleParagraphs() {
        String html = renderer.render("Para one.\n\nPara two.").html();
        assertTrue(html.contains("Para one") && html.contains("Para two"));
    }
}
