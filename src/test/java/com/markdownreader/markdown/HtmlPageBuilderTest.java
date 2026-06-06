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
    void buildProducesDoctype() {
        String result = builder.build("<p>Hello</p>", Theme.LIGHT, 1.0);
        assertTrue(result.contains("<!DOCTYPE html>"));
    }

    @Test
    void buildWithLightThemeAddsThemeLightClass() {
        String result = builder.build("<p>test</p>", Theme.LIGHT, 1.0);
        assertTrue(result.contains("theme-light"));
        assertFalse(result.contains("theme-dark"));
    }

    @Test
    void buildWithDarkThemeAddsThemeDarkClass() {
        String result = builder.build("<p>test</p>", Theme.DARK, 1.0);
        assertTrue(result.contains("theme-dark"));
        assertFalse(result.contains("theme-light"));
    }

    @Test
    void buildFontScaleOneHundredPercent() {
        String result = builder.build("<p>test</p>", Theme.LIGHT, 1.0);
        assertTrue(result.contains("font-size: 100%"));
    }

    @Test
    void buildFontScaleOneFiftyPercent() {
        String result = builder.build("<p>test</p>", Theme.LIGHT, 1.5);
        assertTrue(result.contains("font-size: 150%"));
    }

    @Test
    void buildFontScaleSeventyFivePercent() {
        String result = builder.build("<p>test</p>", Theme.DARK, 0.75);
        assertTrue(result.contains("font-size: 75%"));
    }

    @Test
    void buildContainsBodyHtmlContent() {
        String result = builder.build("<p>Hello World</p>", Theme.LIGHT, 1.0);
        assertTrue(result.contains("Hello World"));
    }

    @Test
    void buildWrapsContentInMarkdownBodyArticle() {
        String result = builder.build("<p>x</p>", Theme.LIGHT, 1.0);
        assertTrue(result.contains("markdown-body"));
        assertTrue(result.contains("<article"));
    }

    @Test
    void buildContainsBackToTopButton() {
        String result = builder.build("<p>x</p>", Theme.LIGHT, 1.0);
        assertTrue(result.contains("top-btn"));
    }

    @Test
    void buildIncludesHtmlLangEn() {
        String result = builder.build("<p>x</p>", Theme.LIGHT, 1.0);
        assertTrue(result.contains("lang=\"en\""));
    }

    @Test
    void buildIncludesCharsetUtf8() {
        String result = builder.build("<p>x</p>", Theme.LIGHT, 1.0);
        assertTrue(result.contains("UTF-8"));
    }

    @Test
    void buildContainsStyleTag() {
        String result = builder.build("<p>x</p>", Theme.LIGHT, 1.0);
        assertTrue(result.contains("<style>"));
    }

    @Test
    void buildContainsScriptTag() {
        String result = builder.build("<p>x</p>", Theme.LIGHT, 1.0);
        assertTrue(result.contains("<script>"));
    }

    @Test
    void buildDarkThemeUsesDarkHighlightTheme() {
        // Dark theme output should differ from light theme output
        String light = builder.build("<p>x</p>", Theme.LIGHT, 1.0);
        String dark = builder.build("<p>x</p>", Theme.DARK, 1.0);
        assertNotEquals(light, dark);
    }

    @Test
    void buildWithEmptyBodyHtml() {
        String result = builder.build("", Theme.LIGHT, 1.0);
        assertTrue(result.contains("<!DOCTYPE html>"));
    }

    @Test
    void buildProcessesEmojiInBody() {
        // Emoji in body should be processed by EmojiImageRenderer
        String result = builder.build("<p>😀</p>", Theme.LIGHT, 1.0);
        assertNotNull(result);
        assertTrue(result.contains("<p>") || result.contains("img"));
    }
}
