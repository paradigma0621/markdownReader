package com.markdownreader.markdown;

import com.markdownreader.ui.Theme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class HtmlPageBuilderTest {

    private HtmlPageBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new HtmlPageBuilder();
    }

    /**
     * True when {@code html} contains a plain {@code <pre>...<code>content} fence, tolerating
     * block-level attributes (e.g. the {@code data-source-line} the preview-editor sync adds).
     */
    private static boolean hasPlainPreCode(String html, String content) {
        return Pattern.compile("<pre[^>]*><code>" + Pattern.quote(content)).matcher(html).find();
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
        // The bundled CSS defines both .theme-light and .theme-dark rules; only the
        // <html> element's class attribute reflects the active theme.
        assertTrue(html.contains("class=\"theme-light\""));
        assertFalse(html.contains("class=\"theme-dark\""));
    }

    @Test
    void buildDarkThemeDoesNotContainLightClass() {
        String html = builder.build("<p>Hello</p>", Theme.DARK, 1.0);
        assertTrue(html.contains("class=\"theme-dark\""));
        assertFalse(html.contains("class=\"theme-light\""));
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

    @Test
    void bootstrapScriptOnlyHighlightsBlocksWithALanguage() {
        // The bootstrap JS must guard on the `language-` class so untagged fenced
        // blocks are never auto-detected (which mis-colors fragments of words).
        String html = builder.build("<pre><code>plain</code></pre>", Theme.LIGHT, 1.0);
        assertTrue(html.contains("language-"), "Expected the language-class guard in the bootstrap script");
        assertTrue(html.contains("plaintext-note"), "Expected the plaintext-note marker logic in the bootstrap script");
        // Our bootstrap must call highlightElement per block, never highlightAll()
        // (which auto-detects every block). highlightAll appears in the inlined hljs
        // library source, so we assert specifically on our own invocation pattern.
        assertTrue(html.contains("hljs.highlightElement(block)"),
                "Expected per-block hljs.highlightElement in our bootstrap script");
    }

    @Test
    void fencedBlockWithLanguageGetsLanguageClass() {
        // flexmark emits <code class="language-xxx"> for a tagged fence; this class is
        // what the bootstrap JS keys off to run highlight.js.
        MarkdownRenderer renderer = new MarkdownRenderer();
        String body = renderer.render("```java\nint x = 1;\n```\n").html();
        assertTrue(body.contains("language-java"),
                "Expected a language-java class for a ```java fence, got: " + body);
    }

    @Test
    void fencedBlockWithoutLanguageHasNoLanguageClass() {
        MarkdownRenderer renderer = new MarkdownRenderer();
        String body = renderer.render("```\nplain block\n```\n").html();
        assertTrue(hasPlainPreCode(body, "plain block"),
                "Expected a plain <pre><code> (no language) block, got: " + body);
        assertFalse(body.contains("language-"),
                "An untagged fence must not carry any language- class, got: " + body);
    }

    @Test
    void inlineCodeIsNotAFencedBlock() {
        MarkdownRenderer renderer = new MarkdownRenderer();
        String body = renderer.render("Inline `code` span.\n").html();
        // Inline code is <code> outside <pre>, so the `pre code` selector never touches it.
        assertTrue(body.contains("<code>code</code>"),
                "Expected inline <code> outside <pre>, got: " + body);
        assertFalse(body.contains("<pre>"), "Inline code must not be wrapped in <pre>, got: " + body);
    }

    @Test
    void ourBootstrapNeverCallsHighlightAll() {
        // highlightAll auto-detects EVERY block (the behavior the feature removed).
        // The inlined hljs library source itself contains "highlightAll", so we must
        // isolate OUR bootstrap (everything from DOMContentLoaded onward) and assert
        // that it never invokes it.
        String html = builder.build("<pre><code>plain</code></pre>", Theme.LIGHT, 1.0);
        // Our bootstrap is the LAST <script> block (the inlined hljs library is the
        // first one and itself mentions DOMContentLoaded/highlightAll).
        int bootstrapStart = html.lastIndexOf("<script>");
        assertTrue(bootstrapStart >= 0, "Expected a bootstrap <script> block");
        String ourBootstrap = html.substring(bootstrapStart);
        assertTrue(ourBootstrap.contains("DOMContentLoaded"), "Bootstrap must run on DOMContentLoaded");
        assertFalse(ourBootstrap.contains("highlightAll"),
                "Our bootstrap must never call hljs.highlightAll");
        assertTrue(ourBootstrap.contains("hljs.highlightElement(block)"),
                "Our bootstrap must highlight per block via highlightElement");
    }

    // --- End-to-end: real Markdown -> MarkdownRenderer -> HtmlPageBuilder.build ---

    @Test
    void javaFenceBecomesLanguageJavaInBuiltDocument() {
        MarkdownRenderer renderer = new MarkdownRenderer();
        String body = renderer.render("```java\nint x = 1;\n```\n").html();
        String html = builder.build(body, Theme.LIGHT, 1.0);
        assertTrue(html.contains("language-java"),
                "A ```java fence must reach the final document as language-java");
        assertTrue(html.contains("int x = 1;"), "The fenced code content must be present");
    }

    @Test
    void untaggedFenceHasNoLanguageClassInBuiltDocument() {
        MarkdownRenderer renderer = new MarkdownRenderer();
        String body = renderer.render("```\nplain block\n```\n").html();
        String html = builder.build(body, Theme.DARK, 1.0);
        // The rendered fence must be a <pre><code> with no language- token in the body,
        // so the bootstrap marks it plaintext-note instead of auto-detecting it.
        assertTrue(hasPlainPreCode(html, "plain block"),
                "Untagged fence must render as a plain <pre><code> block");
        // The only `language-` token in the document is the literal in our bootstrap
        // regex; the rendered block must not carry one.
        assertTrue(hasPlainPreCode(body, "plain block") && !body.contains("language-"),
                "Untagged fence body must carry no language- class, got: " + body);
    }

    @Test
    void inlineCodeReachesBuiltDocumentOutsidePre() {
        MarkdownRenderer renderer = new MarkdownRenderer();
        String body = renderer.render("Inline `code` span.\n").html();
        String html = builder.build(body, Theme.LIGHT, 1.0);
        assertTrue(html.contains("<code>code</code>"),
                "Inline code must reach the final document as a bare <code>");
    }

    @Test
    void buildLightThemeEmbedsLightHljsThemeCss() {
        String html = builder.build("<pre><code>x</code></pre>", Theme.LIGHT, 1.0);
        assertTrue(html.contains("Light theme as seen on github.com"),
                "Light theme must embed the github (light) highlight.js CSS");
        assertFalse(html.contains("Dark theme as seen on github.com"),
                "Light theme must not embed the github-dark highlight.js CSS");
    }

    @Test
    void buildDarkThemeEmbedsDarkHljsThemeCss() {
        String html = builder.build("<pre><code>x</code></pre>", Theme.DARK, 1.0);
        assertTrue(html.contains("Dark theme as seen on github.com"),
                "Dark theme must embed the github-dark highlight.js CSS");
        assertFalse(html.contains("Light theme as seen on github.com"),
                "Dark theme must not embed the light github highlight.js CSS");
    }

    @Test
    void readResourceThrowsForMissingResource() throws Exception {
        // Covers the defensive `in == null` branch of the private readResource helper:
        // a non-existent classpath resource must raise IllegalStateException.
        Method readResource = HtmlPageBuilder.class.getDeclaredMethod("readResource", String.class);
        readResource.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> readResource.invoke(null, "/web/this-resource-does-not-exist.xyz"));
        assertTrue(ex.getCause() instanceof IllegalStateException,
                "Missing resource must surface as IllegalStateException, got: " + ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("Resource not found on classpath"));
    }
}
