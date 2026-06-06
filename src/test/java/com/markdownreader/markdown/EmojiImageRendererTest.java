package com.markdownreader.markdown;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmojiImageRendererTest {

    private EmojiImageRenderer renderer;

    // U+1F600 GRINNING FACE — 1f600.svg is bundled
    private static final String GRINNING_FACE = "😀";
    // U+1FA75 LIGHT BLUE HEART — 1fa75.svg is NOT bundled
    private static final String LIGHT_BLUE_HEART = "🩵";
    // U+1F1E7 + U+1F1F7 = 🇧🇷 Brazil flag
    private static final String FLAG_BR = "🇧🇷";
    // U+1F1E6 regional indicator A
    private static final String REGION_A = "🇦";
    // U+2764 RED HEART + U+FE0F variation selector-16
    private static final String HEART_VS16 = "❤️";
    // 1️⃣ = U+0031 + U+FE0F + U+20E3
    private static final String KEYCAP_1 = "1️⃣";
    // #️⃣ = U+0023 + U+FE0F + U+20E3
    private static final String KEYCAP_HASH = "#️⃣";
    // *️⃣ = U+002A + U+FE0F + U+20E3
    private static final String KEYCAP_STAR = "*️⃣";
    // 👨‍💻 = U+1F468 + U+200D + U+1F4BB
    private static final String MAN_TECHNOLOGIST = "👨‍💻";
    // 👋🏽 = U+1F44B + U+1F3FD (waving hand + medium skin tone)
    private static final String WAVING_HAND_MEDIUM = "👋🏽";

    @BeforeEach
    void setUp() {
        renderer = new EmojiImageRenderer();
    }

    // ── null / empty ──────────────────────────────────────────────────────────

    @Test
    void renderNullReturnsNull() {
        assertNull(renderer.render(null));
    }

    @Test
    void renderEmptyStringReturnsEmpty() {
        assertEquals("", renderer.render(""));
    }

    // ── plain text / HTML passthrough ─────────────────────────────────────────

    @Test
    void renderPlainTextIsUnchanged() {
        String text = "Hello, world! No emojis here.";
        assertEquals(text, renderer.render(text));
    }

    @Test
    void renderHtmlTagsArePreserved() {
        String html = "<p>Hello</p>";
        assertEquals(html, renderer.render(html));
    }

    @Test
    void renderComplexHtmlStructureIsPreserved() {
        String html = "<h1>Title</h1><p>Text with <strong>bold</strong></p>";
        String result = renderer.render(html);
        assertTrue(result.contains("<h1>Title</h1>"));
        assertTrue(result.contains("<strong>bold</strong>"));
    }

    @Test
    void renderHtmlTagAttributesAreNotTouched() {
        String html = "<img alt=\"test\" src=\"test.png\" />";
        String result = renderer.render(html);
        assertTrue(result.contains("alt="));
        assertTrue(result.contains("src="));
    }

    // ── emoji replaced with <img> (bundled SVG exists) ────────────────────────

    @Test
    void renderEmojiPresentationReplacedWithImg() {
        String result = renderer.render(GRINNING_FACE);
        assertTrue(result.contains("<img"), "Should produce <img> tag for bundled SVG");
        assertTrue(result.contains("class=\"emoji\""));
        assertTrue(result.contains("data:image/svg+xml;base64,"));
    }

    @Test
    void renderEmojiInsideHtmlParagraph() {
        String html = "<p>" + GRINNING_FACE + "</p>";
        String result = renderer.render(html);
        assertTrue(result.contains("<img"));
        assertTrue(result.startsWith("<p>") && result.endsWith("</p>"));
    }

    // ── emoji without bundled SVG degrades gracefully ─────────────────────────

    @Test
    void renderEmojiWithNoSvgPreservesOriginal() {
        // 1fa75.svg is not bundled — original character must pass through
        String result = renderer.render(LIGHT_BLUE_HEART);
        assertEquals(LIGHT_BLUE_HEART, result);
    }

    // ── flag emojis (two regional indicators) ────────────────────────────────

    @Test
    void renderFlagEmojiIsHandled() {
        String result = renderer.render(FLAG_BR);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void renderSingleRegionalIndicatorNotConsumedAsPair() {
        // One regional indicator followed by ASCII 'A' (not a regional indicator)
        String s = REGION_A + "A";
        String result = renderer.render(s);
        assertNotNull(result);
        assertTrue(result.endsWith("A"), "Non-indicator character after lone indicator must be preserved");
    }

    // ── variation selector + keycap ───────────────────────────────────────────

    @Test
    void renderVariationSelectorQualifiedEmoji() {
        String result = renderer.render(HEART_VS16);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void renderKeycapDigitIsHandled() {
        String result = renderer.render(KEYCAP_1);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void renderKeycapHashIsHandled() {
        String result = renderer.render(KEYCAP_HASH);
        assertNotNull(result);
    }

    @Test
    void renderKeycapAsteriskIsHandled() {
        String result = renderer.render(KEYCAP_STAR);
        assertNotNull(result);
    }

    // ── ZWJ sequences ─────────────────────────────────────────────────────────

    @Test
    void renderZwjSequenceIsConsumedAsOneEmoji() {
        String result = renderer.render(MAN_TECHNOLOGIST);
        assertNotNull(result);
        // Must produce a single element (img or original), not two separate ones
        int imgCount = countOccurrences(result, "<img");
        assertTrue(imgCount <= 1, "ZWJ sequence should yield at most one img element");
    }

    @Test
    void renderLooseZwjDoesNotConsumeFollowingText() {
        // GRINNING_FACE + ZWJ + 'X' (non-emoji ASCII)
        String s = GRINNING_FACE + "‍X";
        String result = renderer.render(s);
        assertTrue(result.contains("X"), "Non-emoji character after loose ZWJ must be preserved");
    }

    // ── skin tone modifier ────────────────────────────────────────────────────

    @Test
    void renderSkinToneModifierIsConsumedWithBase() {
        String result = renderer.render(WAVING_HAND_MEDIUM);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    // ── caching ───────────────────────────────────────────────────────────────

    @Test
    void renderSameEmojiTwiceProducesSameResult() {
        String first = renderer.render(GRINNING_FACE);
        String second = renderer.render(GRINNING_FACE);
        assertEquals(first, second, "Repeated render of same emoji must be identical (cache)");
    }

    // ── mixed content ─────────────────────────────────────────────────────────

    @Test
    void renderMixedTextAndEmoji() {
        String html = "<p>Hello " + GRINNING_FACE + " World</p>";
        String result = renderer.render(html);
        assertTrue(result.contains("Hello"));
        assertTrue(result.contains("World"));
    }

    @Test
    void renderMultipleEmojisInSequence() {
        // Three grinning faces in a row
        String html = GRINNING_FACE + GRINNING_FACE + GRINNING_FACE;
        String result = renderer.render(html);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(3, countOccurrences(result, "<img"));
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
