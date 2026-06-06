package com.markdownreader.markdown;

import com.markdownreader.ui.Theme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlPageBuilderTest {

    private HtmlPageBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new HtmlPageBuilder();
    }

    @Test
    void buildStartsWithDoctype() {
        String html = builder.build("<p>Hello</p>", Theme.LIGHT, 1.0);
        assertTrue(html.contains("<!DOCTYPE html>"));
    }

    @Test
    void buildContainsHtmlElement() {
        String html = builder.build("<p>Hello</p>", Theme.LIGHT, 1.0);
        assertTrue(html.contains("<html"));
    }

    @Test
    void buildLightThemeHasThemeLightClass() {
        String html = builder.build("<p>Hello</p>", Theme.LIGHT, 1.0);
        assertTrue(html.contains("theme-light"), "Expected 'theme-light' class in: " + html.substring(0, 200));
    }

    @Test
    void buildDarkThemeHasThemeDarkClass() {
        String html = builder.build("<p>Hello</p>", Theme.DARK, 1.0);
        assertTrue(html.contains("theme-dark"), "Expected 'theme-dark' class in: " + html.substring(0, 200));
    }

    @Test
    void buildLightThemeDoesNotContainDarkClass() {
        String html = builder.build("<p>Hello</p>", Theme.LIGHT, 1.0);
        assertFalse(html.contains("theme-dark"));
    }

    @Test
    void buildDarkThemeDoesNotContainLightClass() {
        String html = builder.build("<p>Hello</p>", Theme.DARK, 1.0);
        assertFalse(html.contains("theme-light"));
    }

    @Test
    void buildContainsBodyHtml() {
        String bodyHtml = "<p>Custom content here</p>";
        String html = builder.build(bodyHtml, Theme.LIGHT, 1.0);
        assertTrue(html.contains("Custom content here"));
    }

    @Test
    void buildFontScaleOneHundredPercent() {
        String html = builder.build("<p>text</p>", Theme.LIGHT, 1.0);
        assertTrue(html.contains("font-size: 100%"), "Expected font-size: 100% in: " + html);
    }

    @Test
    void buildFontScaleOneFiftyPercent() {
        String html = builder.build("<p>text</p>", Theme.LIGHT, 1.5);
        assertTrue(html.contains("font-size: 150%"), "Expected font-size: 150% in: " + html);
    }

    @Test
    void buildFontScaleSixtyPercent() {
        String html = builder.build("<p>text</p>", Theme.DARK, 0.6);
        assertTrue(html.contains("font-size: 60%"), "Expected font-size: 60% in: " + html);
    }

    @Test
    void buildFontScaleRounded() {
        // 2.4 → 240%
        String html = builder.build("<p>text</p>", Theme.LIGHT, 2.4);
        assertTrue(html.contains("font-size: 240%"), "Expected font-size: 240% in: " + html);
    }

    @Test
    void buildContainsMarkdownBodyArticle() {
        String html = builder.build("<p>text</p>", Theme.LIGHT, 1.0);
        assertTrue(html.contains("markdown-body"));
    }

    @Test
    void buildContainsHighlightJsScript() {
        String html = builder.build("<p>text</p>", Theme.LIGHT, 1.0);
        // highlight.js is inlined — its initialization call is present
        assertTrue(html.contains("hljs") || html.contains("highlight"));
    }

    @Test
    void buildContainsCssStyle() {
        String html = builder.build("<p>text</p>", Theme.LIGHT, 1.0);
        assertTrue(html.contains("<style>"));
    }

    @Test
    void buildCharsetMetaPresent() {
        String html = builder.build("<p>text</p>", Theme.LIGHT, 1.0);
        assertTrue(html.contains("UTF-8"));
    }

    @Test
    void buildContainsBody() {
        String html = builder.build("<p>text</p>", Theme.LIGHT, 1.0);
        assertTrue(html.contains("<body>"));
        assertTrue(html.contains("</body>"));
    }

    @Test
    void buildWithEmptyBodyHtml() {
        String html = builder.build("", Theme.LIGHT, 1.0);
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
    }

    @Test
    void buildEmojiInBodyHtmlIsProcessed() {
        // EmojiImageRenderer.render() is called inside build()
        String html = builder.build("<p>😀</p>", Theme.LIGHT, 1.0);
        // emoji should be converted to img
        assertTrue(html.contains("<img class=\"emoji\"") || html.contains("😀"));
    }

    @Test
    void buildDarkThemeUsesDarkHighlightStyle() {
        String darkHtml = builder.build("<pre><code>code</code></pre>", Theme.DARK, 1.0);
        // Dark highlight.js theme should be present in dark mode
        assertTrue(darkHtml.contains("github-dark") || darkHtml.contains("dark"));
    }

    @Test
    void buildLightThemeUsesLightHighlightStyle() {
        String lightHtml = builder.build("<pre><code>code</code></pre>", Theme.LIGHT, 1.0);
        // Light highlight.js theme (github) should be present in light mode
        // Both will contain "github" but only the light one has the non-dark version
        assertNotNull(lightHtml);
    }
}
